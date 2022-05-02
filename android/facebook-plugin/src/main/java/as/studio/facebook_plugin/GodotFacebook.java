package as.studio.facebook_plugin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookDialogException;
import com.facebook.FacebookException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.FacebookRequestError;
import com.facebook.FacebookSdk;
import com.facebook.FacebookServiceException;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.FacebookAuthorizationException;
import com.facebook.Profile;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.applinks.AppLinkData;
import com.facebook.gamingservices.GameRequestDialog;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.share.Sharer;
import com.facebook.share.model.GameRequestContent;
import com.facebook.share.model.ShareHashtag;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.MessageDialog;
import com.facebook.share.widget.ShareDialog;

import org.godotengine.godot.Dictionary;
import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GodotFacebook extends GodotPlugin {

    private static final String ON_LOGIN = "on_login";
    private static final String GET_LOGIN_STATUS = "get_login_status";
    private static final String SHOW_DIALOG_RESPONSE = "show_dialog_response";
    private static final String GRAPH_CALL = "graph_call";
    private static final String GET_DEFERRED_APP_LINK = "get_deferred_app_link";
    private static final String REAUTHORIZE = "reauthorize";

    private static final int CALLBACK_ERROR_CODE = 0;
    private static final int CALLBACK_SUCCESS_CODE = 1;

    private static final int INVALID_ERROR_CODE = -2; //-1 is FacebookRequestError.INVALID_ERROR_CODE
    @SuppressWarnings("serial")
    private static final Set<String> OTHER_PUBLISH_PERMISSIONS = new HashSet<String>() {
        {
            add("ads_management");
            add("create_event");
            add("rsvp_event");
        }
    };
    private final String TAG = "ConnectPlugin";

    private CallbackManager callbackManager;
    private AppEventsLogger logger;
    private boolean lastReauthorize = false;
    private boolean lastGraphCall = false;
    private String lastGraphRequestMethod = null;
    private String graphPath;
    private ShareDialog shareDialog;
    private GameRequestDialog gameRequestDialog;
    private MessageDialog messageDialog;

    public GodotFacebook(Godot godot) {
        super(godot);
    }

    @Override
    public void onGodotSetupCompleted() {
        super.onGodotSetupCompleted();

        // create callbackManager
        callbackManager = CallbackManager.Factory.create();

        // create AppEventsLogger
        logger = AppEventsLogger.newLogger(getActivity().getApplicationContext());

        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(final LoginResult loginResult) {
                GraphRequest.newMeRequest(loginResult.getAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject jsonObject, GraphResponse response) {
                        if (response.getError() != null) {
                            if (lastGraphCall) {
                                emitSignal(GRAPH_CALL, CALLBACK_ERROR_CODE, getFacebookRequestErrorResponse(response.getError()));
                            }
                            return;
                        }

                        // If this login comes after doing a new permission request
                        // make the outstanding graph call
                        if (lastGraphCall) {
                            makeGraphCall(lastGraphRequestMethod);
                            return;
                        }

                        Log.d(TAG, "returning login object " + jsonObject.toString());
                        emitSignal(ON_LOGIN, CALLBACK_SUCCESS_CODE, getResponse());

                        if (lastReauthorize) {
                            emitSignal(REAUTHORIZE, CALLBACK_SUCCESS_CODE, getResponse());
                            lastReauthorize = false;
                        }
                    }
                }).executeAsync();
            }

            @Override
            public void onCancel() {
                FacebookOperationCanceledException e = new FacebookOperationCanceledException();
                handleError(e, ON_LOGIN);

                if (lastReauthorize) {
                    handleError(e, REAUTHORIZE);
                    lastReauthorize = false;
                }
            }

            @Override
            public void onError(FacebookException e) {
                Log.e("Activity", String.format("Error: %s", e.toString()));
                handleError(e, ON_LOGIN);

                if (lastReauthorize) {
                    handleError(e, REAUTHORIZE);
                    lastReauthorize = false;
                }

                // Sign-out current instance in case token is still valid for previous user
                if (e instanceof FacebookAuthorizationException) {
                    if (AccessToken.getCurrentAccessToken() != null) {
                        LoginManager.getInstance().logOut();
                    }
                }
            }
        });

        shareDialog = new ShareDialog(getActivity());
        shareDialog.registerCallback(callbackManager, new FacebookCallback<Sharer.Result>() {
            @Override
            public void onSuccess(Sharer.Result result) {
                emitSignal(SHOW_DIALOG_RESPONSE, CALLBACK_SUCCESS_CODE, result.getPostId());
            }

            @Override
            public void onCancel() {
                FacebookOperationCanceledException e = new FacebookOperationCanceledException();
                handleError(e, SHOW_DIALOG_RESPONSE);
            }

            @Override
            public void onError(FacebookException e) {
                Log.e("Activity", String.format("Error: %s", e.toString()));
                handleError(e, SHOW_DIALOG_RESPONSE);
            }
        });

        messageDialog = new MessageDialog(getActivity());
        messageDialog.registerCallback(callbackManager, new FacebookCallback<Sharer.Result>() {
            @Override
            public void onSuccess(Sharer.Result result) {
                emitSignal(SHOW_DIALOG_RESPONSE, CALLBACK_SUCCESS_CODE, "");
            }

            @Override
            public void onCancel() {
                FacebookOperationCanceledException e = new FacebookOperationCanceledException();
                handleError(e, SHOW_DIALOG_RESPONSE);
            }

            @Override
            public void onError(FacebookException e) {
                Log.e("Activity", String.format("Error: %s", e.toString()));
                handleError(e, SHOW_DIALOG_RESPONSE);
            }
        });

        gameRequestDialog = new GameRequestDialog(getActivity());
        gameRequestDialog.registerCallback(callbackManager, new FacebookCallback<GameRequestDialog.Result>() {
            @Override
            public void onSuccess(GameRequestDialog.Result result) {
                Dictionary dictionary = new Dictionary();
                dictionary.put("requestId", result.getRequestId());
                dictionary.put("recipientsIds", result.getRequestRecipients());
                emitSignal(SHOW_DIALOG_RESPONSE, CALLBACK_SUCCESS_CODE, dictionary);
            }

            @Override
            public void onCancel() {
                FacebookOperationCanceledException e = new FacebookOperationCanceledException();
                handleError(e, SHOW_DIALOG_RESPONSE);
            }

            @Override
            public void onError(@NonNull FacebookException e) {
                Log.e("Activity", String.format("Error: %s", e));
                handleError(e, SHOW_DIALOG_RESPONSE);
            }
        });
    }

    @Nullable
    @Override
    public View onMainCreate(Activity activity) {
        AppEventsLogger.activateApp(getActivity().getApplication());
        return super.onMainCreate(activity);
    }

    @Override
    public void onMainResume() {
        super.onMainResume();
        // Developers can observe how frequently users activate their app by logging an app activation event.
        AppEventsLogger.activateApp(getActivity().getApplication());
    }

    @Override
    public void onMainActivityResult(int requestCode, int resultCode, Intent data) {
        super.onMainActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "activity result in plugin: requestCode(" + requestCode + "), resultCode(" + resultCode + ")");
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Keep
    @UsedByGodot
    public void setClientToken(String token) {
        FacebookSdk.setClientToken(token);
    }

    @Keep
    @UsedByGodot
    public String getClientToken() {
        return FacebookSdk.getClientToken();
    }

    @Keep
    @UsedByGodot
    public void setApplicationId(String appId) {
        FacebookSdk.setApplicationId(appId);
    }

    @Keep
    @UsedByGodot
    public String getApplicationId() {
        return FacebookSdk.getApplicationId();
    }

    @Keep
    @UsedByGodot
    public String getApplicationName() {
        return FacebookSdk.getApplicationName();
    }

    @Keep
    @UsedByGodot
    public void setApplicationName(String appName) {
        FacebookSdk.setApplicationName(appName);
    }

    @Keep
    @UsedByGodot
    public void login(String[] permissions) {
        Log.d(TAG, "login FB");

        // #568: Reset lastGraphCall in case it would still contains the last graphApi results of a previous session (login -> graphApi -> logout -> login)
        lastGraphCall = false;
        lastGraphRequestMethod = null;

        LoginManager.getInstance().logIn(getActivity(), Arrays.asList(permissions));
    }

    @Keep
    @UsedByGodot
    public boolean isDataAccessExpired() {
        AccessToken token = AccessToken.getCurrentAccessToken();
        return (token != null && token.isDataAccessExpired());
    }

    @Keep
    @UsedByGodot
    public void logout() {
        if (hasAccessToken()) {
            LoginManager.getInstance().logOut();
        }
    }

    @Keep
    @UsedByGodot
    public void getDeferredApplink() {
        AppLinkData.fetchDeferredAppLinkData(getActivity().getApplicationContext(),
                new AppLinkData.CompletionHandler() {
                    @Override
                    public void onDeferredAppLinkDataFetched(AppLinkData appLinkData) {
                        if (appLinkData == null) {
                            emitSignal(GET_DEFERRED_APP_LINK, "");
                        } else {
                            emitSignal(GET_DEFERRED_APP_LINK, appLinkData.getTargetUri().toString());
                        }
                    }
                });
    }

    @Keep
    @UsedByGodot
    public void showDialog(Dictionary dictionary) {
        Map<String, String> params = new HashMap<>();
        String method = null;

        Set<String> keys = dictionary.keySet();

        for (String key : keys) {
            if (key.equals("method")) {
                method = (String) dictionary.get(key);
            } else {
                params.put(key, (String) dictionary.get(key));
            }
        }

        if (method == null) {
            emitSignal(SHOW_DIALOG_RESPONSE, CALLBACK_ERROR_CODE, "No method provided");
        } else if (method.equalsIgnoreCase("apprequests")) {

            if (!GameRequestDialog.canShow()) {
                emitSignal(SHOW_DIALOG_RESPONSE, CALLBACK_ERROR_CODE, "Cannot show dialog");
                return;
            }

            GameRequestContent.Builder builder = new GameRequestContent.Builder();
            if (params.containsKey("message"))
                builder.setMessage(params.get("message"));
            if (params.containsKey("to"))
                builder.setTo(params.get("to"));
            if (params.containsKey("data"))
                builder.setData(params.get("data"));
            if (params.containsKey("title"))
                builder.setTitle(params.get("title"));
            if (params.containsKey("objectId"))
                builder.setObjectId(params.get("objectId"));
            if (params.containsKey("actionType")) {
                try {
                    final GameRequestContent.ActionType actionType = GameRequestContent.ActionType.valueOf(params.get("actionType"));
                    builder.setActionType(actionType);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Discarding invalid argument actionType");
                }
            }
            if (params.containsKey("filters")) {
                try {
                    final GameRequestContent.Filters filters = GameRequestContent.Filters.valueOf(params.get("filters"));
                    builder.setFilters(filters);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Discarding invalid argument filters");
                }
            }

            // Set up the activity result callback to this class
            // getGodot().onActivityResult();

            gameRequestDialog.show(builder.build());

        } else if (method.equalsIgnoreCase("share") || method.equalsIgnoreCase("feed")) {
            if ((params.containsKey("photo_image") && !ShareDialog.canShow(SharePhotoContent.class)) || (!params.containsKey("photo_image") && !ShareDialog.canShow(ShareLinkContent.class))) {
                emitSignal(SHOW_DIALOG_RESPONSE, CALLBACK_ERROR_CODE, "Cannot show dialog");
                return;
            }

            // Set up the activity result callback to this class
            // getGodot().onActivityResult();

            if (params.containsKey("photo_image")) {
                SharePhotoContent content = buildPhotoContent(params);
                shareDialog.show(content);
            } else {
                ShareLinkContent content = buildLinkContent(params);
                shareDialog.show(content);
            }

        } else if (method.equalsIgnoreCase("send")) {
            if (!MessageDialog.canShow(ShareLinkContent.class)) {
                emitSignal(SHOW_DIALOG_RESPONSE, CALLBACK_ERROR_CODE, "Cannot show dialog");
                return;
            }

            ShareLinkContent.Builder builder = new ShareLinkContent.Builder();
            if (params.containsKey("link"))
                builder.setContentUrl(Uri.parse(params.get("link")));

            messageDialog.show(builder.build());

        } else {
            emitSignal(SHOW_DIALOG_RESPONSE, CALLBACK_ERROR_CODE, "Unsupported dialog method");
        }
    }

    @Keep
    @UsedByGodot
    public Dictionary getCurrentProfile() {
        if (Profile.getCurrentProfile() == null) {
            return null;
        }
        return getProfile();
    }

    @Keep
    @UsedByGodot
    public void graphApi(Dictionary dictionary) {
        lastGraphCall = true;

        String requestMethod = null;
        if (dictionary.size() < 3) {
            lastGraphRequestMethod = null;
        } else {
            lastGraphRequestMethod = (String) dictionary.get("method");
            requestMethod = lastGraphRequestMethod;
        }

        graphPath = (String) dictionary.get("graphPath");
        String[] per = (String[]) dictionary.get("permissions");

        final Set<String> permissions = new HashSet<>();
        if (per != null)
            Collections.addAll(permissions, per);

        if (permissions.size() == 0) {
            makeGraphCall(requestMethod);
            return;
        }

        String declinedPermission = null;

        AccessToken accessToken = AccessToken.getCurrentAccessToken();
        if (accessToken.getPermissions().containsAll(permissions)) {
            makeGraphCall(requestMethod);
            return;
        }

        Set<String> declined = accessToken.getDeclinedPermissions();

        // Figure out if we have all permissions
        for (String permission : permissions) {
            if (declined.contains(permission)) {
                declinedPermission = permission;
                break;
            }
        }

        if (declinedPermission != null) {
            emitSignal(GRAPH_CALL, CALLBACK_ERROR_CODE, "This request needs declined permission: " + declinedPermission);
            return;
        }

//        getGodot().onActivityResult();

        LoginManager loginManager = LoginManager.getInstance();
        loginManager.logIn(getActivity(), permissions);
    }

    @Keep
    @UsedByGodot
    public void setAutoLogAppEventsEnabled(boolean enabled) {
        FacebookSdk.setAutoLogAppEventsEnabled(enabled);
    }

    @Keep
    @UsedByGodot
    public void setAdvertiserIDCollectionEnabled(boolean enabled) {
        FacebookSdk.setAdvertiserIDCollectionEnabled(enabled);
    }

    @Keep
    @UsedByGodot
    public void setDataProcessingOptions(String[] options) {
        FacebookSdk.setDataProcessingOptions(options);
    }

    @Keep
    @UsedByGodot
    public void setUserData(Dictionary dictionary) {

        Map<String, String> params = new HashMap<>();

        String[] keys = dictionary.get_keys();
        for (String key : keys)
            params.put(key, (String) dictionary.get(key));

        AppEventsLogger.setUserData(params.get("em"), params.get("fn"), params.get("ln"), params.get("ph"), params.get("db"), params.get("ge"), params.get("ct"), params.get("st"), params.get("zp"), params.get("cn"));
    }

    @Keep
    @UsedByGodot
    public void clearUserData() {
        AppEventsLogger.clearUserData();
    }

    @Keep
    @UsedByGodot
    public void logEvent(Dictionary dictionary) {
        if (dictionary.size() == 0) {
            // Not enough parameters
            return;
        }

        String eventName = (String) dictionary.get("eventName");
        if (dictionary.size() == 1) {
            logger.logEvent(eventName);
            return;
        }

        // Arguments is greater than 1
        Dictionary params = (Dictionary) dictionary.get("parameters");
        Bundle parameters = new Bundle();
        String[] keys = params.get_keys();
        for (String key : keys) {
            Object value = params.get(key);
            if (value instanceof String) {
                parameters.putString(key, (String) value);
            } else if (value instanceof Integer) {
                parameters.putInt(key, (Integer) value);
            }
        }

        if (dictionary.size() == 2) {
            logger.logEvent(eventName, parameters);
        }

        if (dictionary.size() == 3) {
            double value = (Double) dictionary.get("value");
            logger.logEvent(eventName, value, parameters);
        }
    }

    @Keep
    @UsedByGodot
    public void logPurchase(Dictionary dictionary) {
        if (dictionary.size() < 2 || dictionary.size() > 3) {
            return;
        }
        BigDecimal value = new BigDecimal((Double) dictionary.get("value"));
        String currency = (String) dictionary.get("currency");
        if (dictionary.size() == 3) {
            Dictionary params = (Dictionary) dictionary.get("parameters");
            Bundle parameters = new Bundle();
            String[] keys = params.get_keys();
            for (String key : keys) {

                Object valueParam = params.get(key);

                if (valueParam instanceof String) {
                    parameters.putString(key, (String) valueParam);
                } else if (valueParam instanceof Integer) {
                    parameters.putInt(key, (Integer) valueParam);
                }
            }

            logger.logPurchase(value, Currency.getInstance(currency), parameters);
        } else {
            logger.logPurchase(value, Currency.getInstance(currency));
        }
    }

    @Keep
    @UsedByGodot
    public boolean checkHasCorrectPermissions(String[] permissions) {

        List<String> perm = Arrays.asList(permissions);

        if (perm.size() > 0) {
            AccessToken accessToken = AccessToken.getCurrentAccessToken();
            if (accessToken != null)
                return accessToken.getPermissions().containsAll(perm);
        }

        return true;
    }

    @Keep
    @UsedByGodot
    public void reauthorizeDataAccess() {
        lastGraphCall = false;
        lastGraphRequestMethod = null;
        lastReauthorize = true;

        LoginManager.getInstance().reauthorizeDataAccess(getActivity());
    }

    @Keep
    @UsedByGodot
    public void getLoginStatus(boolean force) {
        if (force) {
            AccessToken.refreshCurrentAccessTokenAsync(new AccessToken.AccessTokenRefreshCallback() {
                @Override
                public void OnTokenRefreshed(AccessToken accessToken) {
                    emitSignal(GET_LOGIN_STATUS, getResponse());
                }

                @Override
                public void OnTokenRefreshFailed(FacebookException exception) {
                    emitSignal(GET_LOGIN_STATUS, getResponse());
                }
            });
        } else {
            emitSignal(GET_LOGIN_STATUS, getResponse());
        }
    }

    @Keep
    @UsedByGodot
    public String getAccessToken() {
        if (hasAccessToken())
            return AccessToken.getCurrentAccessToken().getToken();
        return "";
    }

    private SharePhotoContent buildPhotoContent(Map<String, String> paramBundle) {
        SharePhoto.Builder photoBuilder = new SharePhoto.Builder();
        if (!(paramBundle.get("photo_image") instanceof String)) {
            Log.d(TAG, "photo_image must be a string");
        } else {
            try {
                byte[] photoImageData = Base64.decode(paramBundle.get("photo_image"), Base64.DEFAULT);
                Bitmap image = BitmapFactory.decodeByteArray(photoImageData, 0, photoImageData.length);
                photoBuilder.setBitmap(image).setUserGenerated(true);
            } catch (Exception e) {
                Log.d(TAG, "photo_image cannot be decoded");
            }
        }
        SharePhoto photo = photoBuilder.build();
        SharePhotoContent.Builder photoContentBuilder = new SharePhotoContent.Builder();
        photoContentBuilder.addPhoto(photo);

        return photoContentBuilder.build();
    }

    private ShareLinkContent buildLinkContent(Map<String, String> paramBundle) {
        ShareLinkContent.Builder builder = new ShareLinkContent.Builder();
        if (paramBundle.containsKey("href"))
            builder.setContentUrl(Uri.parse(paramBundle.get("href")));
        if (paramBundle.containsKey("link"))
            builder.setContentUrl(Uri.parse(paramBundle.get("link")));
        if (paramBundle.containsKey("quote"))
            builder.setQuote(paramBundle.get("quote"));
        if (paramBundle.containsKey("hashtag"))
            builder.setShareHashtag(new ShareHashtag.Builder().setHashtag(paramBundle.get("hashtag")).build());

        return builder.build();
    }

    // Simple active session check
    private boolean hasAccessToken() {
        AccessToken token = AccessToken.getCurrentAccessToken();

        if (token == null)
            return false;

        return !token.isExpired();
    }

    private void handleError(FacebookException exception, @NonNull String signalName) {
        if (exception.getMessage() != null) {
            Log.e(TAG, exception.toString());
        }
        String errMsg = "Facebook error: " + exception.getMessage();
        int errorCode = INVALID_ERROR_CODE;
        // User clicked "x"
        if (exception instanceof FacebookOperationCanceledException) {
            errMsg = "User cancelled dialog";
            errorCode = 4201;
        } else if (exception instanceof FacebookDialogException) {
            // Dialog error
            errMsg = "Dialog error: " + exception.getMessage();
        }

        emitSignal(signalName, CALLBACK_ERROR_CODE, getErrorResponse(exception, errMsg, errorCode));
    }

    private void makeGraphCall(String requestMethod) {
        //If you're using the paging URLs they will be URLEncoded, let's decode them.
        try {
            graphPath = URLDecoder.decode(graphPath, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        String[] urlParts = graphPath.split("\\?");
        String graphAction = urlParts[0];
        GraphRequest graphRequest = GraphRequest.newGraphPathRequest(AccessToken.getCurrentAccessToken(), graphAction, new GraphRequest.Callback() {
            @Override
            public void onCompleted(@NonNull GraphResponse response) {
                if (response.getError() != null) {
                    emitSignal(GRAPH_CALL, CALLBACK_ERROR_CODE, getFacebookRequestErrorResponse(response.getError()));
                } else {
                    try {
                        JSONObject responseJson = response.getJSONObject();
                        if (responseJson != null)
                            emitSignal(GRAPH_CALL, CALLBACK_SUCCESS_CODE, GodotFacebookUtils.jsonToDictionary(responseJson));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                graphPath = null;
            }
        });

        if (requestMethod != null) {
            graphRequest.setHttpMethod(HttpMethod.valueOf(requestMethod));
        }

        Bundle params = graphRequest.getParameters();

        if (urlParts.length > 1) {
            String[] queries = urlParts[1].split("&");

            for (String query : queries) {
                int splitPoint = query.indexOf("=");
                if (splitPoint > 0) {
                    String key = query.substring(0, splitPoint);
                    String value = query.substring(splitPoint + 1, query.length());
                    params.putString(key, value);
                }
            }
        }

        graphRequest.setParameters(params);
        graphRequest.executeAsync();
    }

    private Dictionary getResponse() {
        Dictionary dictionary = new Dictionary();

        final AccessToken accessToken = AccessToken.getCurrentAccessToken();
        if (hasAccessToken()) {
            long dataAccessExpirationTimeInterval = accessToken.getDataAccessExpirationTime().getTime() / 1000L;
            Date today = new Date();
            long expiresTimeInterval = (accessToken.getExpires().getTime() - today.getTime()) / 1000L;

            Dictionary authResponse = new Dictionary();

            dictionary.put("status", "connected");
            dictionary.put("authResponse", authResponse);
            authResponse.put("accessToken", accessToken.getToken());
            authResponse.put("data_access_expiration_time", Math.max(dataAccessExpirationTimeInterval, 0));
            authResponse.put("expiresIn", Math.max(expiresTimeInterval, 0));
            authResponse.put("userID", accessToken.getUserId());

        } else {
            dictionary.put("status", "unknown");
        }

        return dictionary;
    }

    public Dictionary getFacebookRequestErrorResponse(FacebookRequestError error) {

        Dictionary dictionary = new Dictionary();
        dictionary.put("errorCode", error.getErrorCode());
        dictionary.put("errorType", error.getErrorType());
        dictionary.put("errorMessage", error.getErrorMessage());

        if (error.getErrorUserMessage() != null) {
            dictionary.put("errorUserMessage", error.getErrorUserMessage());
        }

        if (error.getErrorUserTitle() != null) {
            dictionary.put("errorUserTitle", error.getErrorUserTitle());
        }

        return dictionary;
    }

    public Dictionary getErrorResponse(Exception error, String message, int errorCode) {
        if (error instanceof FacebookServiceException) {
            return getFacebookRequestErrorResponse(((FacebookServiceException) error).getRequestError());
        }

        Dictionary dictionary = new Dictionary();

        String response = "{";

        if (error instanceof FacebookDialogException) {
            errorCode = ((FacebookDialogException) error).getErrorCode();
        }

        if (errorCode != INVALID_ERROR_CODE) {
            dictionary.put("errorCode", errorCode);
        }

        if (message == null) {
            message = error.getMessage();
        }

        dictionary.put("errorMessage", message);

        return dictionary;
    }

    public Dictionary getProfile() {
        Dictionary dictionary = new Dictionary();
        final Profile profile = Profile.getCurrentProfile();
        if (profile != null) {
            dictionary.put("userID", profile.getId());
            dictionary.put("firstName", profile.getFirstName());
            dictionary.put("lastName", profile.getLastName());
        }
        return dictionary;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotFacebook";
    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new HashSet<>();

        signals.add(new SignalInfo(ON_LOGIN, Integer.class, Dictionary.class));
        signals.add(new SignalInfo(GET_LOGIN_STATUS, Dictionary.class));
        signals.add(new SignalInfo(SHOW_DIALOG_RESPONSE, Integer.class, String.class));
        signals.add(new SignalInfo(SHOW_DIALOG_RESPONSE, Integer.class, Dictionary.class));
        signals.add(new SignalInfo(GRAPH_CALL, Integer.class, String.class));
        signals.add(new SignalInfo(GRAPH_CALL, Integer.class, Dictionary.class));
        signals.add(new SignalInfo(GET_DEFERRED_APP_LINK, String.class));
        signals.add(new SignalInfo(REAUTHORIZE, Integer.class, Dictionary.class));

        return signals;
    }
}
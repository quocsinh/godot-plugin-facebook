---
layout: page
title: API Reference
permalink: /api/
---

# Get Application ID, Name and Client token
---
`.get_application_id()`

`.get_application_name()`

`.get_client_token()`

# Login
---
`.login(permissions: Array)` See the [Permissions Reference](https://developers.facebook.com/docs/permissions/reference/) for more details

# Logout
---
`.logout()`

# Get Current Profile
---
`.get_current_profile()`

# Check permissions
---
`.check_has_correct_permissions(permissions: Array)`

Returns **true** if all passed permissions are granted.

Returns **false** if any passed permissions are not granted.

# Get Status
---
`.get_login_status(force: bool)`

Setting the **force** parameter to true clears any previously cached status and fetches fresh data from Facebook.
For more information see: [Facebook Documentation](https://developers.facebook.com/docs/reference/javascript/FB.getLoginStatus)

# Check if data access is expired
---
`.is_data_access_expried()`

Returns **true** if data access is expired.

For more information see: [Facebook Documentation](https://developers.facebook.com/docs/facebook-login/auth-vs-data/#testing-when-access-to-user-data-expires)

# Reauthorize data access
---
`.reauthorize_data_access()`

For more information see: [Facebook Documentation](https://developers.facebook.com/docs/facebook-login/auth-vs-data/#data-access-expiration)

# Show a Dialog
---
`.show_dialog(data : Dictionary)`

Example options - Share Dialog:
```markdown
{
    method: "share",
    href: "http://example.com",
    hashtag: '#myHashtag'
}
```

### Share Photo Dialog:
```markdown
{
    method: "share",
    photo_image: "/9j/4TIERXhpZgAATU0AKgAAAA..."
}
```
_photo_image_ must be a Base64-encoded string.

### Game request:
```markdown
{
    method: "apprequests",
    message: "Come on man, check out my application.",
    data: data,
    title: title,
    actionType: 'askfor',
    objectID: 'YOUR_OBJECT_ID', 
    filters: 'app_non_users'
}
```

### Send Dialog:
```markdown
{
    method: "send",
    link: "http://example.com"
}
```
For options information see:

[Facebook share dialog documentation](https://developers.facebook.com/docs/sharing/reference/share-dialog)

[Facebook send dialog documentation](https://developers.facebook.com/docs/sharing/reference/send-dialog)

# The Graph API
---
`.graph_api(data : Dictionary)`
```markdown
{
    method: "GET"
    graphPath: "https://graph.facebook.com/USER-ID?access_token=ACCESS-TOKEN"
    permissions: ["public_profile", "user_birthday"]
}
```
Allows access to the Facebook Graph API. This API allows for additional permission because, unlike login, the Graph API can accept multiple permissions.

**method** is optional and defaults to "GET".

**Note:** "In order to make calls to the Graph API on behalf of a user, the user has to be logged into your app using Facebook login, and you must include the access_token parameter in your graphPath. "

### For more information see:

- [Calling the Graph API](https://developers.facebook.com/docs/android/graph)
- [Graph Explorer](https://developers.facebook.com/tools/explorer)
- [Graph API](https://developers.facebook.com/docs/graph-api)
- [Access Levels](https://developers.facebook.com/docs/graph-api/overview/access-levels)

# Log an Event [App Event](https://developers.facebook.com/docs/app-events/)
---
`.log_event(data: Dictionary)`
```markdown
{
    eventName: String
    parameters: Dictionary
    value: Double
}
```
**name:** name of the event

**params:** extra data to log with the event (is optional)

**valueToSum:** a property which is an arbitrary number that can represent any value (e.g., a price or a quantity). When reported, all of the valueToSum properties will be summed together. For example, if 10 people each purchased one item that cost $10 (and passed in valueToSum) then they would be summed to report a number of $100. (is optional)

# Log a Purchase
---
`.log_purchase(data: Dictionary)`
```markdown
{
    value: Double
    currency: String
    parameters: Dictionary
}
```
**NOTE:** Both _value_ and _currency_ are required. The currency specification is expected to be an ISO 4217 currency code. _parameters_ is optional.

# Data Processing Options
---
`.set_data_processing_options(options : Array)`

This plugin allows developers to set Data Processing Options as part of compliance with the California Consumer Privacy Act (CCPA).

For more information see: [Facebook Documentation](https://developers.facebook.com/docs/app-events/guides/ccpa)

# Advanced Matching
---
`.set_user_data(data : Dictionary)`

With [Advanced Matching](https://developers.facebook.com/docs/app-events/advanced-matching/), Facebook can match conversion events to your customers to optimize your ads and build larger re-marketing audiences.

```markdown
{
    "em": "jsmith@example.com", //email
    "fn": "john", //first name
    "ln": "smith", //last name
    "ph", "16505554444", //phone number
    "db": "19910526", //birthdate
    "ge": "f", //gender
    "ct": "menlopark", //city
    "st": "ca", //state
    "zp": "94025", //zip code
    "cn": "us" //country
}
```
`.clear_user_data()`

# Get Access Token
---
`.get_access_token()`

# GDPR Compliance
---
`set_auto_log_app_events_enabled(enabled : bool)`

This plugin supports Facebook's [GDPR Compliance](https://developers.facebook.com/docs/app-events/gdpr-compliance/) Delaying Automatic Event Collection.

In order to auto-logging after an end User provides consent by calling the `set_auto_log_app_events_enabled` method and set it to true.

# Collection of Advertiser IDs
---
`.set_advertiser_id_collection_enabled(enabled : bool)`

To enable collection by calling the `set_advertiser_id_collection_enabled` method and set it to true.

# App Ads and Deep Links
---
`.get_deferred_app_link()`

See the [Facebook Developer documentation](https://developers.facebook.com/docs/app-ads/deep-linking/) for more details.

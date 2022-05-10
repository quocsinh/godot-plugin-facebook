---
layout: page
title: Signals
permalink: /signals/
---

# Common convention
---
`code = 1` -> **SUCCESS**.

`code = 0` -> **FAILURE**.

# login_response (code: int, response: Dictionary)
[Be connected with Login](../api/#login)

---
```json
{
  "status": "connected",
  "authResponse": {
    "accessToken": "<long string>",
    "data_access_expiration_time": "1623680244",
    "expiresIn": "5183979",
    "userID": "634565435"
  }
}
```

# login_status_response(response: Dictionary)
[Be connected with Get Status](../api/#get-status)

---
```json
{
  "status": "connected",
  "authResponse": {
    "accessToken": "kgkh3g42kh4g23kh4g2kh34g2kg4k2h4gkh3g4k2h4gk23h4gk2h34gk234gk2h34AndSoOn",
    "data_access_expiration_time": "1623680244",
    "expiresIn": "5183738",
    "userID": "12345678912345"
  }
}
```

# show_dialog_response(code: int, response: Dictionary)
[Be connected with Show a Dialog](../api/#show-a-dialog)

---

# graph_call_response(code: int, response: Dictionary)
[Be connected with The Graph API](../api/#the-graph-api)

---

# deferred_app_link_response(target_uri: String)
[Be connected with App Ads and Deep Links](../api/#app-ads-and-deep-links)

---

# reauthorize_response(code: int, response: Dictionary)
[Be connected with Reauthorize data access](../api/#reauthorize-data-access)

---

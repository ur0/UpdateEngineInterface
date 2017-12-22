# UpdateEngineInterface

This app talks to Android's update\_engine daemon, allowing a user to flash
block based OTAs which haven't been assigned to their device by the OEM.

Currently, strings for the Xiaomi Mi A1 have been hardcoded (this is set to
change, as I plan to make this app universally compatible).

Note that the following requirements exist on a standard device -
- If you want to bring your own OTA, the signature for the payload.bin must 
be valid (not a concern if you're not modifying it)
- This app must have *system* priviliges, i.e. it must be installed into
`/system/priv-app` or equivalent. The current approach is to wrap this into a
Magisk module, which greatly simplifies the process for the average user.

## License, etc.
This is licensed under the MIT license in order to be as permissive to other
developers who might wish to make their own forks. See the LICENSE.txt file
for the complete notice.

This app uses Firebase analytics and user device IDs are temproarily stored at
Firebase in plaintext. A backend application later hashes them and removes
them from Cloud Firestore. These hashes are then used to track downloads and
are removed once the download is complete.

Copyright 2017, Umang Raghuvanshi

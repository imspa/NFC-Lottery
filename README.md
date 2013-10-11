NFC Lottery
===========

An Android application that **simplifies the handling of raffles and lotteries**, where a prize is given out to a random person amongst the participants.

[![Install NFC Lottery from the Google Play Store](http://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=it.imwatch.nfclottery)

---

## Features

The app has some really interesting features:

 - Has a **simple yet polished and modern UI**
 - Supports both **manual participant data input** and **quick, NFC-based participant entry** (ideal, for example, at conferences where each attendant has an NFC-enabled badge)
 - Relies on an internal **ContentProvider**
 - Supports **backing up the participants database to CSV**, for easy manipulation of data after the contest ends
 - Supports **backing up the participants database to, as well as restoring from, Dropbox**, using their Sync APIs
 - Has a **dedicated winner announcement UI**, which also **reads the winner name aloud** using TTS
 - Allows you to **see the previous winners list**, and **revoke victories**, directly from within the app

The app doesn't have a tablet-optimized layout, mostly because it really doesn't need one.

---

## Authors

The **NFC Lottery** app has been developed by [**Daniele Bonaldo**](https://github.com/dany.bony) and [**Sebastiano Poggi**](https://github.com/rock3r) from the [i'm Watch](http://www.imsmart.com) R&D team.

The app has originally been developer in three days for the [Droidcon Paris 2013](http://fr.droidcon.com/2013), and has been used at the closing keynote to choose the winners for an i'm Watch giveaway that took place during the event.

The app has been refactored, improved and made more reliable by Sebastiano Poggi for the [Droidcon London 2013](http://uk.droidcon.com/2013) and prepared for its FOSS release.

---

## License

NFC Lottery is released under the [**Apache 2.0 license**](http://www.apache.org/licenses/LICENSE-2.0).

---

## Attributions

This app uses the following open-source libraries, whose authors we wish to thank:

 - _Android Support Library v4 and AppCompat v7_, by Google Inc. ([Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0))
 - _Crouton_, by Benjamin Weiss ([Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0))
 - _CardMe_, by George El-Haddad ([BSD License](http://sourceforge.net/p/cardme/code/HEAD/tree/trunk/licenses/license.txt)), ported to Android
 - _OpenCsv_, by Glen Smith, Kyle Miller, Scott Conway and Sean Sullivan ([Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0))
 - _Dropbox Sync SDK_ by Dropbox Inc.
MaterialAudiobookPlayer
=======================
[![CI Status](https://circleci.com/gh/PaulWoitaschek/MaterialAudiobookPlayer.svg?&style=shield&circle-token=3e7023d997fb5667ed60f751f963aaaf8c1f02d4)](https://circleci.com/gh/PaulWoitaschek/MaterialAudiobookPlayer)

This is my digital playground where I am learning. I'm integrating and validating new technologies and ideas here, playing around with new UI / UX components and developing with the best coding standard I come up with.

At the same time I want to provide an audiobook player which is really easy in use and a joy to work with.
There are still some components outdated.

If you like to [contribute](#contrib) or [translate](#trans), that is very appreciated. But there might be impacting structural changes over time.

<a href="https://play.google.com/store/apps/details?id=de.ph1b.audiobook"><img src="https://github.com/Ph1b/MaterialAudiobookPlayer/blob/master/Images/map-port.jpg" width="400" ></a>

[![Download from Google Play](http://www.android.com/images/brand/android_app_on_play_large.png "Download from Google Play")](https://play.google.com/store/apps/details?id=de.ph1b.audiobook)
[![Material Audiobook Player on fdroid.org](https://camo.githubusercontent.com/7df0eafa4433fa4919a56f87c3d99cf81b68d01c/68747470733a2f2f662d64726f69642e6f72672f77696b692f696d616765732f632f63342f462d44726f69642d627574746f6e5f617661696c61626c652d6f6e2e706e67 "Download from fdroid.org")](https://f-droid.org/repository/browse/?fdfilter=material&fdid=de.ph1b.audiobook)

# <a name="contrib">Contribution</a>

## Development

If you wan to help check the label [PR-Welcome](https://github.com/PaulWoitaschek/MaterialAudiobookPlayer/issues?q=is%3Aopen+is%3Aissue+label%3A%22PR+welcome%22). 
Its always a good idea to talk about what are going to do before you actually start it, so frustration can be avoided.

Some rules for coding:
* Use the code style the project uses
* For each feature, make a seperate branch, so it can be reviewed separately
* Use commits with a good description, so one can see, what you did

### Building the project on windows
If you want to build the project on windows you will run into some issues since the pre-build scripts use linux specific commands. 

To fix the build you'll have to download the exoplayer libraries from 
[here (opus)](https://ftp.osuosl.org/pub/xiph/releases/opus/opus-1.1.4.tar.gz) and 
[here (flac)](https://ftp.osuosl.org/pub/xiph/releases/flac/flac-1.3.1.tar.xz)
 and unzip those files to the exoplayer-flac and exoplayer-opus submodules' src/main/jni folders.
 This step has to be done only once and whenever the versions of the libraries change so you should only have to do this once.
 
 The current version for the flac file can be found [here](https://github.com/PaulWoitaschek/MaterialAudiobookPlayer/blob/master/buildSrc/src/main/java/de/ph1b/audiobook/ndkGen/PrepareFlac.kt#L23)
 and for the opus version [here](https://github.com/PaulWoitaschek/MaterialAudiobookPlayer/blob/master/buildSrc/src/main/java/de/ph1b/audiobook/ndkGen/PrepareOpus.kt#L30)

## <a name="trans">Translations</a>
The project page is on [transifex](https://www.transifex.com/projects/p/material-audiobook-player/). There all the localizations are maintained. If you want to contribute, check if there are untranslated or wrong translated words. 

Or you can start translating a new language if you speak it ;-)

# License
Copyright (C) 2014 [Paul Woitaschek](http://www.paul-woitaschek.de/)

The license is [GnuGPLv3](https://github.com/Ph1b/MaterialAudiobookPlayer/blob/master/LICENSE.md). With contributing you agree to license your code under the same conditions.

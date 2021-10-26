# Premo

[![Maven Central](https://img.shields.io/maven-central/v/me.dmdev.premo/premo)](https://search.maven.org/artifact/me.dmdev.premo/premo)
[![License: MIT](https://img.shields.io/github/license/dmdevgo/premo?color=yellow)](https://github.com/dmdevgo/Premo/blob/master/LICENSE)
![platform-android](https://img.shields.io/badge/platform-android-green)
![platform-ios](https://img.shields.io/badge/platform-ios-lightgrey)

Premo is a Kotlin Multiplatform library that helps you implement the Presentation Layer and share it on iOS and Android. Focus on writing logic instead of solving common and boring UI related issues.

Here are some key features:
- **State holding** — PresentationModel is a multiplatform version of the ViewModel.
- **Lifecycle** — Presentation Model is lifecycle aware.
- **Navigation** — navigate between Presentation Models instead of switching views.
- **Persistence** — saving and restoring state after recreating a process.
- **Communication** — send messages from children towards the root Presentation Model.

At the same time, the library provides freedom of choice:
- **Architecture** — MVVM, PM, MVI or other.
- **UI** — Jetpack Compose for Android and SwiftUI for iOS.

## Table of Contents

- [Overview](#overview)
- [Installation](#installation)
- [Sample](#sample)
- [License](#license)

## Overview

The library is based on the ideas of the [Presentation Model](https://martinfowler.com/eaaDev/PresentationModel.html) pattern described by Martin Fowler. The Presentation Model stores the state for presentation and coordinates with the domain model layer. 

In practice, you will have not one Presentation Model, but a composition of child Presentation Models. At the base, there will be a root Presentation Model, from which a tree of children will grow. Children can be pushed into the Stack Navigator, thus organizing the navigation stack.

Such a tree composition is well suited for a hierarchical view as well as for a composition of functions from declarative UI frameworks.

<img src="/docs/images/premo_diagram.jpg" width="500px">

One interesting feature of the tree is that the child keeps a reference to the parent. This allows for messaging between Presentation Models. For example, when some event occurs in a child that requires a reaction from the parent. In this case, the child can send a message towards the root for one of the parents to handle it.

<img src="/docs/images/premo_messaging.jpg" width="500px">

Navigation commands can be one such event. The Child PM sends a message and then the Parent PM can intercept this message and push the next Child PM into the Stack Navigator.

This way of navigation has several advantages:
- Relieves the Child PM from the responsibility of what transition to make and allow the parents to decide.
- Simplifies nested navigation when an event occurs deep in the hierarchy.

## Installation

Add to the root gradle script:
```Kotlin
allprojects {
    repositories {
        mavenCentral()
    }
}
```
Add dependencies to the shared multiplatform module:
```Kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api("me.dmdev.premo:premo:<latest_version>")
                api("me.dmdev.premo:premo-navigation:<latest_version>")
            }
        } 
    }
}
```

Note that the library depends on the multithreaded version of coroutines(native-mt). You will also need [kotlinx serialization](https://github.com/Kotlin/kotlinx.serialization) to implement the `PmStateSaver`. See [sample](#sample) for more details.

> Attention! The library is in the pre-release alpha version. Stable work and backward compatibility are not guaranteed. API may be changed in the future.

## Sample

[Sample](https://github.com/dmdevgo/Premo/tree/master/sample) demonstrates:
- Sharing presentation logic between Android and iOS.
- UI on Jetpack Compose and SwiftUI.
- Using StateFlow to implement MVVM (PM).
- Simple Counter.
- Stack and Bottom navigation with multistack.
- Saving and restoring the state after killing the process.

## License

```
The MIT License (MIT)

Copyright (c) 2020-2021 Dmitriy Gorbunov (dmitriy.goto@gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

//
// Copyright (c) ZeroC, Inc. All rights reserved.
//

#pragma once

[["js:es6-module"]]

module Demo
{
    interface Hello
    {
        idempotent void sayHello(int delay);
        void shutdown();
    }
}

/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view;

import android.annotation.NonNull;
import android.os.IBinder;

/**
 * Provides low-level communication with the system window manager for
 * operations that are bound to a particular context, display or parent window.
 * Instances of this object are sensitive to the compatibility info associated
 * with the running application.
 * <p>
 * This object implements the {@link ViewManager} interface,
 * allowing you to add any View subclass as a top-level window on the screen.
 * Additional window manager specific layout parameters are defined for
 * control over how windows are displayed.  It also implements the {@link WindowManager}
 * interface, allowing you to control the displays attached to the device.
 * <p>
 * <p>Applications will not normally use WindowManager directly, instead relying
 * on the higher-level facilities in {@link android.app.Activity} and
 * {@link android.app.Dialog}.
 * <p>
 * <p>Even for low-level window manager access, it is almost never correct to use
 * this class.  For example, {@link android.app.Activity#getWindowManager}
 * provides a window manager for adding windows that are associated with that
 * activity -- the window manager will not normally allow you to add arbitrary
 * windows that are not associated with an activity.
 *
 * @hide
 * @see WindowManager
 * @see WindowManagerGlobal
 */
public final class WindowManagerImpl implements WindowManager {
    private final WindowManagerGlobal mGlobal = WindowManagerGlobal.getInstance();
    private final Display mDisplay;
    private final Window mParentWindow;

    private IBinder mDefaultToken;

    public WindowManagerImpl(Display display) {
        this(display, null);
    }

    private WindowManagerImpl(Display display, Window parentWindow) {
        mDisplay = display;
        mParentWindow = parentWindow;
    }

    public WindowManagerImpl createLocalWindowManager(Window parentWindow) {
        //parentWindow就是activity的Window
        return new WindowManagerImpl(mDisplay, parentWindow);
    }

    public WindowManagerImpl createPresentationWindowManager(Display display) {
        return new WindowManagerImpl(display, mParentWindow);
    }

    /**
     * Sets the window token to assign when none is specified by the client or
     * available from the parent window.
     *
     * @param token The default token to assign.
     */
    public void setDefaultToken(IBinder token) {
        mDefaultToken = token;
    }

    @Override
    public void addView(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
        //applyDefaultToken会检查，有没有设置默认token，如果设置，而且没有设置父窗口的情况下，token又是null的话，直接把默认token赋值给token吧。
        applyDefaultToken(params);
        mGlobal.addView(view, params, mDisplay, mParentWindow);
    }

    @Override
    public void updateViewLayout(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
        applyDefaultToken(params);
        mGlobal.updateViewLayout(view, params);
    }

    private void applyDefaultToken(@NonNull ViewGroup.LayoutParams params) {
        // Only use the default token if we don't have a parent window.
        if (mDefaultToken != null && mParentWindow == null) {
            if (!(params instanceof LayoutParams)) {
                throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
            }

            // Only use the default token if we don't already have a token.
            final LayoutParams wparams = (LayoutParams) params;
            if (wparams.token == null) {
                wparams.token = mDefaultToken;
            }
        }
    }

    @Override
    public void removeView(View view) {
        mGlobal.removeView(view, false);
    }

    @Override
    public void removeViewImmediate(View view) {
        mGlobal.removeView(view, true);
    }

    @Override
    public Display getDefaultDisplay() {
        return mDisplay;
    }
}

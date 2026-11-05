/*
 * SPDX-FileCopyrightText: 2014-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.content.res;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.ArraySet;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lineageos.app.LineageContextConstants;

public class ThemeComponentManager {
    private static final String TAG = ThemeComponentManager.class.getSimpleName();

    private static IThemeComponent sService;
    private static ThemeComponentManager sInstance;
    private Context mContext;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Set<ThemeChangeListener> mChangeListeners = new ArraySet<>();

    private ThemeComponentManager(Context context) {
        Context appContext = context.getApplicationContext();
        mContext = appContext == null ? context : appContext;
        sService = getService();

        if (context.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.THEME) && sService == null) {
            throw new RuntimeException("Unable to get ThemeComponentService. The service" +
                    " either crashed, was not started, or the interface has been called too early" +
                    " in SystemServer init");
        }
    }

    /**
     * Get or create an instance of the {@link lineageos.content.res.ThemeComponentManager}
     *
     * @param context Used to get the service
     * @return {@link ThemeComponentManager}
     */
    public static synchronized ThemeComponentManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ThemeComponentManager(context);
        }

        return sInstance;
    }

    /** @hide **/
    public static IThemeComponent getService() {
        if (sService != null) {
            return sService;
        }

        IBinder b = ServiceManager.getService(LineageContextConstants.LINEAGE_THEME_SERVICE);
        sService = IThemeComponent.Stub.asInterface(b);

        if (sService == null) {
            Log.e(TAG, "null theme component service, SAD!");
            return null;
        }

        return sService;
    }

    private final IThemeChangeListener mThemeChangeListener = new IThemeChangeListener.Stub() {
        @Override
        public void onProgress(final int progress) {
            mHandler.post(() -> {
                for (ThemeChangeListener listener : getChangeListeners()) {
                    listener.onProgress(progress);
                }
            });
        }

        @Override
        public void onFinish(final boolean isSuccess) {
            mHandler.post(() -> {
                for (ThemeChangeListener listener : getChangeListeners()) {
                    listener.onFinish(isSuccess);
                }
            });
        }
    };

    // Listeners may unregister themselves when notified, notify a copy
    private List<ThemeChangeListener> getChangeListeners() {
        synchronized (mChangeListeners) {
            return new ArrayList<>(mChangeListeners);
        }
    }

    /**
     * Register a {@link ThemeChangeListener} to be notified about theme change progress.
     * @param listener {@link ThemeChangeListener} to register
     */
    public void registerThemeChangeListener(@NonNull ThemeChangeListener listener) {
        synchronized (mChangeListeners) {
            if (mChangeListeners.contains(listener)) {
                throw new IllegalArgumentException("Listener already registered");
            }
            if (mChangeListeners.isEmpty()) {
                try {
                    sService.requestThemeChangeUpdates(mThemeChangeListener);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
            mChangeListeners.add(listener);
        }
    }

    /**
     * Unregister a {@link ThemeChangeListener}
     * @param listener {@link ThemeChangeListener} to unregister
     */
    public void unregisterThemeChangeListener(@NonNull ThemeChangeListener listener) {
        synchronized (mChangeListeners) {
            if (mChangeListeners.remove(listener) && mChangeListeners.isEmpty()) {
                try {
                    sService.removeUpdates(mThemeChangeListener);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        }
    }

    /**
     * Apply the given components of a theme for the calling user.
     */
    public void requestThemeChange(@NonNull String pkgName, @NonNull List<String> components) {
        final ThemeChangeRequest.Builder builder = new ThemeChangeRequest.Builder();
        for (String component : components) {
            builder.setComponent(component, pkgName);
        }
        requestThemeChange(builder.build());
    }

    public void requestThemeChange(@NonNull Map<String, String> componentMap) {
        final ThemeChangeRequest.Builder builder = new ThemeChangeRequest.Builder();
        for (Map.Entry<String, String> entry : componentMap.entrySet()) {
            builder.setComponent(entry.getKey(), entry.getValue());
        }
        requestThemeChange(builder.build());
    }

    public void requestThemeChange(@NonNull ThemeChangeRequest request) {
        try {
            sService.requestThemeChange(request, mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Apply the theme configured as the device default, or reset to the system if none is set.
     */
    public void applyDefaultTheme() {
        try {
            sService.applyDefaultTheme(mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean isThemeApplying() {
        try {
            return sService.isThemeApplying();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int getProgress() {
        try {
            return sService.getProgress();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * @return the overlay, icon and font theme mapping for the calling user.
     */
    @NonNull
    public ThemeConfig getThemeConfig() {
        try {
            final ThemeConfig config = sService.getThemeConfig(mContext.getUserId());
            return config != null ? config : ThemeConfig.getSystemTheme();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * @return a map of theme component to the theme package applied for it, for the calling
     *         user. Components that use the system default are absent.
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public Map<String, String> getAppliedComponents() {
        try {
            final Map<String, String> components =
                    sService.getAppliedComponents(mContext.getUserId());
            return components != null ? components : Collections.emptyMap();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public long getLastThemeChangeTime() {
        try {
            return sService.getLastThemeChangeTime(mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Nullable
    public ThemeChangeRequest.RequestType getLastThemeChangeRequestType() {
        try {
            final int type = sService.getLastThemeChangeRequestType(mContext.getUserId());
            final ThemeChangeRequest.RequestType[] types = ThemeChangeRequest.RequestType.values();
            return type >= 0 && type < types.length ? types[type] : null;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public interface ThemeChangeListener {
        void onProgress(int progress);
        void onFinish(boolean isSuccess);
    }
}

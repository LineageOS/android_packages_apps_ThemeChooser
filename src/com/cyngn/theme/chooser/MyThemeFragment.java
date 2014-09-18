/*
 * Copyright (C) 2014 The Cyanogen, Inc
 */
package com.cyngn.theme.chooser;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ThemeUtils;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ThemesContract;
import android.provider.ThemesContract.PreviewColumns;
import android.provider.ThemesContract.ThemesColumns;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.cyngn.theme.util.AudioUtils;
import com.cyngn.theme.util.PreferenceUtils;
import com.cyngn.theme.util.ThemedTypefaceHelper;
import com.cyngn.theme.util.TypefaceHelperCache;
import com.cyngn.theme.util.Utils;

import java.io.IOException;
import java.util.Map;

public class MyThemeFragment extends ThemeFragment {
    private static final String TAG = MyThemeFragment.class.getSimpleName();

    private static final String ARG_BASE_THEME_PACKAGE_NAME = "baseThemePkgName";
    private static final String ARG_BASE_THEME_NAME = "baseThemeName";
    private static final String ARG_BASE_THEME_AUTHOR = "baseThemeAuthor";

    private String mBaseThemeName;
    private String mBaseThemeAuthor;

    private SurfaceView mSurfaceView;

    static MyThemeFragment newInstance(String baseThemePkgName, String baseThemeName,
                                       String baseThemeAuthor, boolean skipLoadingAnim) {
        MyThemeFragment f = new MyThemeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE_NAME, CURRENTLY_APPLIED_THEME);
        args.putString(ARG_BASE_THEME_PACKAGE_NAME, baseThemePkgName);
        args.putString(ARG_BASE_THEME_NAME, baseThemeName);
        args.putString(ARG_BASE_THEME_AUTHOR, baseThemeAuthor);
        args.putBoolean(ARG_SKIP_LOADING_ANIM, skipLoadingAnim);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Context context = getActivity();
        ThemedTypefaceHelper helper = sTypefaceHelperCache.getHelperForTheme(context,
                getAppliedFontPackageName());
        mTypefaceNormal = helper.getTypeface(Typeface.NORMAL);
        mBaseThemePkgName = getArguments().getString(ARG_BASE_THEME_PACKAGE_NAME);
        mBaseThemeName = getArguments().getString(ARG_BASE_THEME_NAME);
        mBaseThemeAuthor = getArguments().getString(ARG_BASE_THEME_AUTHOR);
        mSurfaceView = createSurfaceView();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        mThemeTagLayout.setAppliedTagEnabled(true);
        if (mBaseThemePkgName.equals(ThemeUtils.getDefaultThemePackageName(getActivity()))) {
            mThemeTagLayout.setDefaultTagEnabled(true);
        }
        if (PreferenceUtils.hasThemeBeenUpdated(getActivity(), mBaseThemePkgName)) {
            mThemeTagLayout.setUpdatedTagEnabled(true);
        }
        setCustomizedTagIfCustomized();
        mDelete.setVisibility(View.GONE);
        mReset.setVisibility(View.VISIBLE);
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mExpanded && getLoaderManager().getLoader(0) != null) {
            getLoaderManager().restartLoader(0, null, this);
        }

        IntentFilter filter = new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
        getActivity().registerReceiver(mWallpaperChangeReceiver, filter);
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(mWallpaperChangeReceiver);
        super.onPause();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (mThemeTagLayout == null) return;

        if (!isVisibleToUser) {
            if (PreferenceUtils.hasThemeBeenUpdated(getActivity(), mBaseThemePkgName)) {
                mThemeTagLayout.setUpdatedTagEnabled(true);
            }
        } else {
            if (PreferenceUtils.hasThemeBeenUpdated(getActivity(), mBaseThemePkgName)) {
                PreferenceUtils.removeUpdatedTheme(getActivity(), mBaseThemePkgName);
            }
        }
    }

    @Override
    protected boolean onPopupMenuItemClick(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_reset:
                resetTheme();
                return true;
        }

        return super.onPopupMenuItemClick(item);
    }

    @Override
    public void collapse(boolean applyTheme) {
        super.collapse(applyTheme);
        if (mSurfaceView != null) mSurfaceView.setVisibility(View.VISIBLE);
    }

    @Override
    public void expand() {
        super.expand();
        if (mSurfaceView != null && mShadowFrame.indexOfChild(mSurfaceView) >= 0) {
            mSurfaceView.setVisibility(View.GONE);
            mWallpaper.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void performClick(boolean clickedOnContent) {
        if (clickedOnContent) {
            showCustomizeResetLayout();
        } else {
            if (isShowingCustomizeResetLayout()) {
                hideCustomizeResetLayout();
            }
        }
    }

    private BroadcastReceiver mWallpaperChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final WallpaperManager wm = WallpaperManager.getInstance(context);
            if (wm.getWallpaperInfo() != null) {
                addSurfaceView(mSurfaceView);
            } else {
                removeSurfaceView(mSurfaceView);
            }

            Drawable wp = context == null ? null : wm.getDrawable();
            if (wp != null) {
                mWallpaper.setImageDrawable(wp);
                mWallpaperCard.setWallpaper(wp);
            }
        }
    };

    private void setCustomizedTagIfCustomized() {
        boolean isDefault =
                mBaseThemePkgName.equals(ThemeUtils.getDefaultThemePackageName(getActivity()));
        if (isDefault) {
            // The default theme could be a mix of the system default theme and holo so lets check
            // that.  i.e. Hexo with holo for the components not found in Hexo
            Map<String, String> defaultComponents = ThemeUtils.getDefaultComponents(getActivity());
            for (String component : mCurrentTheme.keySet()) {
                String componentPkgName = mCurrentTheme.get(component);
                if (defaultComponents.containsKey(component)) {
                    if (!componentPkgName.equals(defaultComponents.get(component))) {
                        mThemeTagLayout.setCustomizedTagEnabled(true);
                        break;
                    }
                } else {
                    mThemeTagLayout.setCustomizedTagEnabled(true);
                    break;
                }
            }
        } else {
            for (String pkgName : mCurrentTheme.values()) {
                if (!pkgName.equals(mBaseThemePkgName)) {
                    mThemeTagLayout.setCustomizedTagEnabled(true);
                    break;
                }
            }
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri uri;
        String[] projection;
        switch (id) {
            case LOADER_ID_ALL:
                if (args != null) {
                    String pkgName = args.getString(ARG_PACKAGE_NAME);
                    if (pkgName != null) {
                        return super.onCreateLoader(id, args);
                    }
                }
                projection = new String[]{
                        PreviewColumns.WALLPAPER_PREVIEW,
                        PreviewColumns.STATUSBAR_BACKGROUND,
                        PreviewColumns.STATUSBAR_WIFI_ICON,
                        PreviewColumns.STATUSBAR_WIFI_COMBO_MARGIN_END,
                        PreviewColumns.STATUSBAR_BLUETOOTH_ICON,
                        PreviewColumns.STATUSBAR_SIGNAL_ICON,
                        PreviewColumns.STATUSBAR_CLOCK_TEXT_COLOR,
                        PreviewColumns.STATUSBAR_BATTERY_CIRCLE,
                        PreviewColumns.STATUSBAR_BATTERY_LANDSCAPE,
                        PreviewColumns.STATUSBAR_BATTERY_PORTRAIT,
                        PreviewColumns.NAVBAR_BACK_BUTTON,
                        PreviewColumns.NAVBAR_HOME_BUTTON,
                        PreviewColumns.NAVBAR_RECENT_BUTTON,
                        PreviewColumns.ICON_PREVIEW_1,
                        PreviewColumns.ICON_PREVIEW_2,
                        PreviewColumns.ICON_PREVIEW_3,
                        PreviewColumns.LOCK_WALLPAPER_PREVIEW,
                        PreviewColumns.STYLE_PREVIEW,
                        PreviewColumns.NAVBAR_BACKGROUND
                };
                uri = PreviewColumns.APPLIED_URI;
                return new CursorLoader(getActivity(), uri, projection, null, null, null);
            default:
                // Only LOADER_ID_ALL differs for MyThemeFragment
                return super.onCreateLoader(id, args);
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
        super.onLoadFinished(loader, c);
        // if the theme is resetting, we need to apply these changes now that the supported
        // theme components have been properly set.
        if (mThemeResetting && loader.getId() == LOADER_ID_ALL) {
            applyTheme();
        }
    }

    @Override
    protected void populateSupportedComponents(Cursor c) {
    }

    @Override
    protected Boolean shouldShowComponentCard(String component) {
        return true;
    }

    @Override
    protected void loadTitle(Cursor c) {
        mTitle.setText(mBaseThemeName);
        mAuthor.setText(mBaseThemeAuthor);
    }

    @Override
    protected void loadWallpaper(Cursor c, boolean animate) {
        mExternalWallpaperUri = null;
        int pkgNameIdx = c.getColumnIndex(ThemesContract.ThemesColumns.PKG_NAME);
        if (pkgNameIdx > -1) {
            super.loadWallpaper(c, animate);
            return;
        }
        Drawable overlay = null;
        if (animate) {
            overlay = getOverlayDrawable(mWallpaperCard, true);
        }

        int wpIdx = c.getColumnIndex(PreviewColumns.WALLPAPER_PREVIEW);
        final Resources res = getResources();
        final Context context = getActivity();
        final WallpaperManager wm = WallpaperManager.getInstance(context);
        if (wm.getWallpaperInfo() != null) {
            addSurfaceView(mSurfaceView);
        } else {
            removeSurfaceView(mSurfaceView);
        }

        Drawable wp = context == null ? null : wm.getDrawable();
        if (wp == null) {
            Bitmap bmp = Utils.loadBitmapBlob(c, wpIdx);
            if (bmp != null) wp = new BitmapDrawable(res, bmp);
        }
        if (wp != null) {
            mWallpaper.setImageDrawable(wp);
            mWallpaperCard.setWallpaper(wp);
            setCardTitle(mWallpaperCard, mCurrentTheme.get(ThemesColumns.MODIFIES_LAUNCHER),
                    getString(R.string.wallpaper_label));
        } else {
            mWallpaperCard.clearWallpaper();
            mWallpaperCard.setEmptyViewEnabled(true);
            setAddComponentTitle(mWallpaperCard, getString(R.string.wallpaper_label));
        }

        if (animate) {
            animateContentChange(R.id.wallpaper_card, mWallpaperCard, overlay);
        }
    }

    @Override
    protected void loadLockScreen(Cursor c, boolean animate) {
        mExternalLockscreenUri = null;
        int pkgNameIdx = c.getColumnIndex(ThemesContract.ThemesColumns.PKG_NAME);
        if (pkgNameIdx > -1) {
            super.loadLockScreen(c, animate);
            return;
        }
        Drawable overlay = null;
        if (animate) {
            overlay = getOverlayDrawable(mLockScreenCard, true);
        }

        int wpIdx = c.getColumnIndex(PreviewColumns.LOCK_WALLPAPER_PREVIEW);
        final Resources res = getResources();
        final Context context = getActivity();
        Drawable wp = context == null ? null :
                WallpaperManager.getInstance(context).getFastKeyguardDrawable();
        if (wp == null) {
            Bitmap bmp = Utils.loadBitmapBlob(c, wpIdx);
            if (bmp != null) wp = new BitmapDrawable(res, bmp);
        }
        if (wp != null) {
            mLockScreenCard.setWallpaper(wp);
        } else {
            mLockScreenCard.clearWallpaper();
            mLockScreenCard.setEmptyViewEnabled(true);
            setAddComponentTitle(mLockScreenCard, getString(R.string.lockscreen_label));
        }

        if (animate) {
            animateContentChange(R.id.lockscreen_card, mLockScreenCard, overlay);
        }
    }

    @Override
    protected void loadFont(Cursor c, boolean animate) {
        int pkgNameIdx = c.getColumnIndex(ThemesContract.ThemesColumns.PKG_NAME);
        if (pkgNameIdx > -1) {
            super.loadFont(c, animate);
            return;
        }
        Drawable overlay = null;
        if (animate) {
            overlay = getOverlayDrawable(mFontPreview, true);
        }
        setCardTitle(mFontCard, mCurrentTheme.get(ThemesColumns.MODIFIES_FONTS),
                getString(R.string.font_label));

        TypefaceHelperCache cache = TypefaceHelperCache.getInstance();
        ThemedTypefaceHelper helper = cache.getHelperForTheme(getActivity(),
                getAppliedFontPackageName());
        mTypefaceNormal = helper.getTypeface(Typeface.NORMAL);
        mFontPreview.setTypeface(mTypefaceNormal);
        if (animate) {
            animateContentChange(R.id.font_preview_container, mFontPreview, overlay);
        }
    }

    @Override
    protected void loadAudible(int type, Cursor c, boolean animate) {
        int pkgNameIdx = c.getColumnIndex(ThemesContract.ThemesColumns.PKG_NAME);
        if (pkgNameIdx > -1) {
            super.loadAudible(type, c, animate);
            return;
        }
        ComponentCardView audibleContainer = null;
        ImageView playPause = null;
        String modsComponent = "";
        switch (type) {
            case RingtoneManager.TYPE_RINGTONE:
                audibleContainer = mRingtoneCard;
                playPause = mRingtonePlayPause;
                modsComponent = ThemesColumns.MODIFIES_RINGTONES;
                break;
            case RingtoneManager.TYPE_NOTIFICATION:
                audibleContainer = mNotificationCard;
                playPause = mNotificationPlayPause;
                modsComponent = ThemesColumns.MODIFIES_NOTIFICATIONS;
                break;
            case RingtoneManager.TYPE_ALARM:
                audibleContainer = mAlarmCard;
                playPause = mAlarmPlayPause;
                modsComponent = ThemesColumns.MODIFIES_ALARMS;
                break;
        }
        if (audibleContainer == null) return;

        if (playPause == null) {
            playPause =
                    (ImageView) audibleContainer.findViewById(R.id.play_pause);
        }
        TextView title = (TextView) audibleContainer.findViewById(R.id.audible_name);
        MediaPlayer mp = mMediaPlayers.get(playPause);
        if (mp == null) {
            mp = new MediaPlayer();
        }
        final Context context = getActivity();
        Uri ringtoneUri;
        try {
            ringtoneUri = AudioUtils.loadDefaultAudible(context, type, mp);
        } catch (IOException e) {
            Log.w(TAG, "Unable to load default sound ", e);
            return;
        }
        if (ringtoneUri != null) {
            title.setText(RingtoneManager.getRingtone(context, ringtoneUri).getTitle(context));
        } else {
            title.setText(getString(R.string.audible_title_none));
            playPause.setVisibility(View.INVISIBLE);
        }
        setCardTitle(audibleContainer, mCurrentTheme.get(modsComponent),
                getAudibleLabel(type));

        playPause.setTag(mp);
        mMediaPlayers.put(playPause, mp);
        playPause.setOnClickListener(mPlayPauseClickListener);
        mp.setOnCompletionListener(mPlayCompletionListener);
    }

    @Override
    public String getThemePackageName() {
        if (mBaseThemePkgName == null) {
            // check if the package name is defined in the arguments bundle
            Bundle bundle = getArguments();
            if (bundle != null) {
                mBaseThemePkgName = bundle.getString(ARG_BASE_THEME_PACKAGE_NAME);
            }
        }
        return mBaseThemePkgName;
    }

    private SurfaceView createSurfaceView() {
        final Context context = getActivity();
        if (context == null) return null;

        SurfaceView sv = new SurfaceView(context);
        final Resources res = context.getResources();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                res.getDimensionPixelSize(R.dimen.wallpaper_preview_width),
                res.getDimensionPixelSize(R.dimen.theme_preview_height),
                Gravity.CENTER_HORIZONTAL);
        sv.setLayoutParams(params);

        return sv;
    }

    private void addSurfaceView(SurfaceView sv) {
        if (mShadowFrame.indexOfChild(mSurfaceView) < 0) {
            int idx = mShadowFrame.indexOfChild(mWallpaper);
            mShadowFrame.addView(sv, idx + 1);
        }
    }

    private void removeSurfaceView(SurfaceView sv) {
        if (mShadowFrame.indexOfChild(mSurfaceView) >= 0) {
            mShadowFrame.removeView(sv);
        }
    }
}
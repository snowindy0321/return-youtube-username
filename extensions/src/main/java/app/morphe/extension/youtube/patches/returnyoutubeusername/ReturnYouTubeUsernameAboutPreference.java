package app.morphe.extension.youtube.patches.returnyoutubeusername;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.net.Uri;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import java.util.Locale;

import app.morphe.extension.shared.Logger;

/**
 * About preference for Return YouTube Username.
 * Opens an official Morphe-style dialog displaying project details, version, and open source links.
 */
@SuppressWarnings("deprecation")
public class ReturnYouTubeUsernameAboutPreference extends Preference {

    public static final String VERSION = "v2.3";
    public static final String REPO_URL = "https://github.com/snowindy0321/return-youtube-username";
    public static final String ADD_SOURCE_URL = "https://morphe.software/add-source?github=snowindy0321/return-youtube-username&name=Return%20YouTube%20Username";
    public static final String RELEASES_URL = "https://github.com/snowindy0321/return-youtube-username/releases";
    public static final String LICENSE_URL = "https://github.com/snowindy0321/return-youtube-username/blob/main/LICENSE";
    public static final String MORPHE_URL = "https://morphe.software";

    public ReturnYouTubeUsernameAboutPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public ReturnYouTubeUsernameAboutPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ReturnYouTubeUsernameAboutPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ReturnYouTubeUsernameAboutPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setOnPreferenceClickListener(pref -> {
            showAboutDialog(pref.getContext());
            return true;
        });
    }

    /**
     * Opens the official Morphe-styled About Dialog.
     */
    public static void showAboutDialog(Context context) {
        if (context == null) return;
        try {
            boolean isDark = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

            Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            float density = context.getResources().getDisplayMetrics().density;
            int pad = (int) (14 * density);

            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(pad, pad, pad, pad);

            // 28dp rounded rectangle background matching Morphe dialog styling
            float radius = 28 * density;
            ShapeDrawable bg = new ShapeDrawable(new RoundRectShape(
                    new float[]{radius, radius, radius, radius, radius, radius, radius, radius},
                    null, null));
            int bgColor = isDark ? Color.parseColor("#1F1F1F") : Color.parseColor("#FFFFFF");
            bg.getPaint().setColor(bgColor);
            layout.setBackground(bg);

            WebView webView = new WebView(context);
            webView.setVerticalScrollBarEnabled(false);
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setBackgroundColor(Color.TRANSPARENT);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception ex) {
                        Logger.printException(() -> "Failed to open link: " + url, ex);
                    }
                    return true;
                }
            });

            String html = buildAboutHtml(isDark);
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
            layout.addView(webView);

            dialog.setContentView(layout);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(window.getAttributes());
                lp.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.90);
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                lp.gravity = Gravity.CENTER;
                window.setAttributes(lp);
            }

            dialog.show();
        } catch (Throwable t) {
            Logger.printException(() -> "Failed to show ReturnYouTubeUsername about dialog", t);
        }
    }

    private static String buildAboutHtml(boolean isDark) {
        Locale loc = Locale.getDefault();
        boolean isZhTW = loc.getLanguage().equals("zh") && (loc.getCountry().equalsIgnoreCase("TW")
                || loc.getCountry().equalsIgnoreCase("HK") || loc.getCountry().equalsIgnoreCase("MO")
                || loc.getScript().equalsIgnoreCase("Hant"));
        boolean isZhCN = loc.getLanguage().equals("zh") && !isZhTW;

        String bg = isDark ? "#1F1F1F" : "#FFFFFF";
        String fg = isDark ? "#E8EAED" : "#202124";
        String accent = isDark ? "#00AFAE" : "#1E5AA8";
        String cardBg = isDark ? "rgba(255, 255, 255, 0.08)" : "rgba(0, 0, 0, 0.04)";
        String cardBorder = isDark ? "rgba(255, 255, 255, 0.12)" : "rgba(0, 0, 0, 0.08)";
        String activeBg = isDark ? "rgba(255, 255, 255, 0.14)" : "rgba(0, 0, 0, 0.08)";

        String statusHeader = isZhTW ? "已是最新版本" : (isZhCN ? "已是最新版本" : "Up to date");
        String statusBody = isZhTW
                ? ("目前安裝版本：" + VERSION)
                : (isZhCN ? ("当前安装版本：" + VERSION)
                          : ("Installed version: " + VERSION));

        String descHeader = isZhTW ? "專案簡介" : (isZhCN ? "项目简介" : "About This Patch");
        String descBody = isZhTW
                ? "為 YouTube Android (Morphe) 原生打造的輕量還原補丁，將留言區、Shorts、即時直播聊天室及標題說明欄之 @頻道代碼，全面智慧還原為頻道顯示名稱。"
                : (isZhCN ? "为 YouTube Android (Morphe) 原生打造的轻量还原补丁，将评论区、Shorts、即时直播聊天室及标题简介栏之 @频道代码，全面智能还原为频道显示名称。"
                          : "Native lightweight patch for YouTube Android (Morphe) that restores @handles in comments, Shorts, live chat, and video titles/descriptions to actual channel names.");

        String compHeader = isZhTW ? "實測相容環境" : (isZhCN ? "实测兼容环境" : "Tested Environments");
        String compBody = "YouTube v21.30.216 · Morphe Manager v2.5.0 · Morphe Patches v2.1.0+";

        String sectionTitle = isZhTW ? "相關連結與資源" : (isZhCN ? "相关链接与资源" : "Links & Resources");
        String repoTitle = isZhTW ? "原始碼倉庫" : (isZhCN ? "源代码仓库" : "Source Code");
        String repoSub = "snowindy0321/return-youtube-username";
        String addSourceTitle = isZhTW ? "一鍵新增來源" : (isZhCN ? "一键新增来源" : "Add to Morphe Manager");
        String addSourceSub = isZhTW ? "自動導入補丁源至 Morphe Manager" : (isZhCN ? "自动导入补丁源至 Morphe Manager" : "One-click add source to Morphe Manager");
        String releasesTitle = isZhTW ? "版本發布歷史" : (isZhCN ? "版本发布历史" : "Release History");
        String releasesSub = isZhTW ? "查看更新日誌與發布頁面" : (isZhCN ? "查看更新日志与发布页面" : "View changelogs and downloads");
        String licenseTitle = isZhTW ? "開源授權" : (isZhCN ? "开源许可证" : "License");
        String licenseSub = "MIT License";
        String creditsTitle = isZhTW ? "專案致謝" : (isZhCN ? "项目致谢" : "Credits");
        String creditsSub = "Morphe Project & Contributors";

        // SVG Icons (Material outlined standard paths)
        String svgLogo = "<svg viewBox=\"0 0 24 24\"><path d=\"M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z\"/></svg>";
        String svgCode = "<svg viewBox=\"0 0 24 24\"><path d=\"M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z\"/></svg>";
        String svgPublic = "<svg viewBox=\"0 0 24 24\"><path d=\"M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z\"/></svg>";
        String svgTag = "<svg viewBox=\"0 0 24 24\"><path d=\"M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7 4.67 7 5.5 6.33 7 5.5 7z\"/></svg>";
        String svgLicense = "<svg viewBox=\"0 0 24 24\"><path d=\"M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z\"/></svg>";
        String svgHeart = "<svg viewBox=\"0 0 24 24\"><path d=\"M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z\"/></svg>";
        String svgChevron = "<svg viewBox=\"0 0 24 24\"><path d=\"M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z\"/></svg>";

        return "<!DOCTYPE html>"
                + "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no\">"
                + "<style>"
                + "* { margin: 0; padding: 0; box-sizing: border-box; }"
                + "body { background: " + bg + "; color: " + fg + "; font-family: Roboto, -apple-system, sans-serif; -webkit-tap-highlight-color: transparent; -webkit-touch-callout: none; -webkit-user-select: none; user-select: none; padding: 8px 4px 16px; }"
                + ".dialog-header { padding: 14px 8px 6px; text-align: center; }"
                + ".app-logo { width: 68px; height: 68px; margin: 0 auto 12px; border-radius: 20px; background: linear-gradient(135deg, #1E5AA8 0%, #00AFAE 100%); display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 14px rgba(0, 0, 0, 0.25); }"
                + ".app-logo svg { width: 38px; height: 38px; fill: #FFFFFF; }"
                + ".app-name { font-size: 19px; font-weight: 700; }"
                + ".info-card { margin-top: 10px; padding: 10px 12px; border-radius: 14px; text-align: center; background: " + cardBg + "; border: 1px solid " + cardBorder + "; }"
                + ".info-card h3 { font-size: 13px; font-weight: 600; color: " + accent + "; margin-bottom: 3px; }"
                + ".info-card p { font-size: 12px; opacity: 0.75; line-height: 1.45; }"
                + ".section { padding: 14px 4px 4px; }"
                + ".section-title { font-size: 14px; font-weight: 700; margin-bottom: 8px; text-align: center; opacity: 0.85; }"
                + ".settings-group { border-radius: 16px; background: " + cardBg + "; border: 1px solid " + cardBorder + "; overflow: hidden; }"
                + ".settings-item { position: relative; display: flex; align-items: center; gap: 12px; padding: 12px 14px; text-decoration: none; color: inherit; }"
                + ".settings-item:active { background: " + activeBg + "; }"
                + ".settings-item + .settings-item::before { content: ''; position: absolute; top: 0; left: 14px; right: 14px; height: 1px; background: " + cardBorder + "; }"
                + ".item-icon { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 26px; height: 26px; }"
                + ".item-icon svg { width: 20px; height: 20px; fill: " + accent + "; }"
                + ".item-text { flex: 1; min-width: 0; }"
                + ".item-title { font-size: 14px; font-weight: 500; }"
                + ".item-subtitle { font-size: 11px; opacity: 0.6; margin-top: 2px; word-break: break-all; }"
                + ".item-chevron { display: flex; align-items: center; flex-shrink: 0; opacity: 0.4; }"
                + ".item-chevron svg { width: 16px; height: 16px; fill: " + accent + "; }"
                + "</style></head><body>"
                + "<div class=\"dialog-header\">"
                + "<div class=\"app-logo\">" + svgLogo + "</div>"
                + "<div class=\"app-name\">Return YouTube Username</div>"
                + "<div class=\"info-card\"><h3>" + statusHeader + "</h3><p>" + statusBody + "</p></div>"
                + "<div class=\"info-card\"><h3>" + descHeader + "</h3><p>" + descBody + "</p></div>"
                + "<div class=\"info-card\"><h3>" + compHeader + "</h3><p>" + compBody + "</p></div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">" + sectionTitle + "</div>"
                + "<div class=\"settings-group\">"
                + "<a href=\"" + REPO_URL + "\" class=\"settings-item\"><span class=\"item-icon\">" + svgCode + "</span><div class=\"item-text\"><div class=\"item-title\">" + repoTitle + "</div><div class=\"item-subtitle\">" + repoSub + "</div></div><span class=\"item-chevron\">" + svgChevron + "</span></a>"
                + "<a href=\"" + ADD_SOURCE_URL + "\" class=\"settings-item\"><span class=\"item-icon\">" + svgPublic + "</span><div class=\"item-text\"><div class=\"item-title\">" + addSourceTitle + "</div><div class=\"item-subtitle\">" + addSourceSub + "</div></div><span class=\"item-chevron\">" + svgChevron + "</span></a>"
                + "<a href=\"" + RELEASES_URL + "\" class=\"settings-item\"><span class=\"item-icon\">" + svgTag + "</span><div class=\"item-text\"><div class=\"item-title\">" + releasesTitle + "</div><div class=\"item-subtitle\">" + releasesSub + "</div></div><span class=\"item-chevron\">" + svgChevron + "</span></a>"
                + "<a href=\"" + LICENSE_URL + "\" class=\"settings-item\"><span class=\"item-icon\">" + svgLicense + "</span><div class=\"item-text\"><div class=\"item-title\">" + licenseTitle + "</div><div class=\"item-subtitle\">" + licenseSub + "</div></div><span class=\"item-chevron\">" + svgChevron + "</span></a>"
                + "<a href=\"" + MORPHE_URL + "\" class=\"settings-item\"><span class=\"item-icon\">" + svgHeart + "</span><div class=\"item-text\"><div class=\"item-title\">" + creditsTitle + "</div><div class=\"item-subtitle\">" + creditsSub + "</div></div><span class=\"item-chevron\">" + svgChevron + "</span></a>"
                + "</div></div>"
                + "</body></html>";
    }
}

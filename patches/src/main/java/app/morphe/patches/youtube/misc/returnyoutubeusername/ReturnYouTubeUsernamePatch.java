package app.morphe.patches.youtube.misc.returnyoutubeusername;

import app.morphe.patcher.PatcherContext;
import app.morphe.patcher.patch.AppTarget;
import app.morphe.patcher.patch.Compatibility;
import app.morphe.patcher.patch.Patch;
import app.morphe.patcher.patch.PatchFunctionsKt;
import app.morphe.patcher.patch.PatchKt;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import kotlin.Unit;

public class ReturnYouTubeUsernamePatch {

    private static final String EXTENSION_CLASS =
            "Lapp/morphe/extension/youtube/patches/ReturnYouTubeUsernamePatch;";

    public static final Patch patch = PatchKt.patch(
            "Return YouTube Username+",
            "Adds an option to replace YouTube handles (@handle) with original channel names in comments, Shorts uploaders, and throughout the app.",
            true,
            def -> {
                def.compatibleWith(new Compatibility(
                        "com.google.android.youtube",
                        "YouTube",
                        null,
                        null,
                        (Integer) null,
                        null,
                        java.util.Collections.singletonList(new AppTarget(
                                (String) null,
                                (java.util.Map) null,
                                false,
                                (Integer) null,
                                (String) null
                        )),
                        null
                ));

                def.extendWith(() -> {
                    ClassLoader cl = ReturnYouTubeUsernamePatch.class.getClassLoader();
                    InputStream is = cl != null ? cl.getResourceAsStream("extensions/youtube.mpe") : null;
                    if (is != null) return is;
                    is = ReturnYouTubeUsernamePatch.class.getResourceAsStream("/extensions/youtube.mpe");
                    if (is != null) return is;
                    return ClassLoader.getSystemResourceAsStream("extensions/youtube.mpe");
                });

                def.execute(() -> {
                    System.out.println("[*] ReturnYouTubeUsernamePatch executing...");
                    PatcherContext ctx = null;
                    try {
                        Field f = PatcherContext.class.getDeclaredField("current");
                        f.setAccessible(true);
                        ctx = (PatcherContext) f.get(null);
                        if (ctx == null) {
                            System.err.println("[!] PatcherContext.current is null; falling back to standard ClassLoader lookup.");
                        }
                    } catch (Throwable t) {
                        System.err.println("[!] Unable to read PatcherContext.current (" + t.getClass().getSimpleName()
                                + ": " + t.getMessage() + "); falling back to standard ClassLoader lookup.");
                    }

                    // 1. Hook Litho text components via dynamic reflection
                    boolean hookedSpannable = false;
                    boolean hookedLitho = false;
                    try {
                        Class<?> tcpClass = findClass(ctx, "app.morphe.patches.shared.misc.textcomponent.TextComponentPatchKt");

                        if (tcpClass == null) {
                            System.err.println("[!] TextComponentPatchKt not found in any ClassLoader! "
                                    + "Ensure official Morphe YouTube patches are loaded in the same patching session, "
                                    + "or check if TextComponentPatchKt moved in this Morphe version.");
                        } else {
                            try {
                                Method hookSpannable = tcpClass.getMethod("hookSpannableString", String.class, String.class, boolean.class);
                                hookSpannable.invoke(null, EXTENSION_CLASS, "preFetchLithoText", false);
                                hookedSpannable = true;
                                System.out.println("[+] Hooked TextComponentPatchKt.hookSpannableString -> " + EXTENSION_CLASS + "->preFetchLithoText");
                            } catch (Throwable t) {
                                Throwable cause = unwrapReflectionError(t);
                                System.err.println("[!] Failed to invoke TextComponentPatchKt.hookSpannableString: "
                                        + cause.getClass().getName() + ": " + cause.getMessage());
                                if (cause.getClass().getName().contains("UninitializedPropertyAccessException")) {
                                    System.err.println("[!] Hint: Morphe's shared TextComponentPatch was not initialized. "
                                            + "Enable at least one official Morphe patch that uses text components (e.g. Hide layout components or Return YouTube Dislike).");
                                }
                                cause.printStackTrace();
                            }

                            try {
                                Method hookLitho = tcpClass.getMethod("hookLithoSpannableString", String.class, String.class);
                                hookLitho.invoke(null, EXTENSION_CLASS, "onLithoTextLoaded");
                                hookedLitho = true;
                                System.out.println("[+] Hooked TextComponentPatchKt.hookLithoSpannableString -> " + EXTENSION_CLASS + "->onLithoTextLoaded");
                            } catch (Throwable t) {
                                Throwable cause = unwrapReflectionError(t);
                                System.err.println("[!] Failed to invoke TextComponentPatchKt.hookLithoSpannableString: "
                                        + cause.getClass().getName() + ": " + cause.getMessage());
                                if (cause.getClass().getName().contains("UninitializedPropertyAccessException")) {
                                    System.err.println("[!] Hint: Morphe's shared Litho text hook was not initialized. "
                                            + "Enable at least one official Morphe patch that depends on lithoSpannableStringPatch.");
                                }
                                cause.printStackTrace();
                            }

                            if (!hookedSpannable || !hookedLitho) {
                                System.err.println("[!] WARNING: Litho text hooking incomplete (hookSpannableString="
                                        + hookedSpannable + ", hookLithoSpannableString=" + hookedLitho
                                        + "). Runtime handle replacement may be degraded or inactive.");
                            }
                        }
                    } catch (Throwable t) {
                        Throwable cause = unwrapReflectionError(t);
                        System.err.println("[!] Unexpected error while locating/hooking TextComponentPatchKt: "
                                + cause.getClass().getName() + ": " + cause.getMessage());
                        cause.printStackTrace();
                    }

                    // 2. Hook Application / Activity init to ensure ReturnYouTubeUsernamePatch is loaded early
                    // This pre-registers all settings before Morphe AbstractPreferenceFragment reads them.
                    boolean hookedAppInit = false;
                    boolean hookedAppOnCreate = false;
                    try {
                        Class<?> appInitClass = findClass(ctx, "app.morphe.patches.youtube.misc.extension.hooks.ApplicationInitHookKt");
                        if (appInitClass != null) {
                            try {
                                Method getHook = appInitClass.getMethod("getApplicationInitHook");
                                Object hook = getHook.invoke(null);
                                if (hook == null) {
                                    System.err.println("[!] ApplicationInitHookKt.getApplicationInitHook() returned null.");
                                } else {
                                    Method invokeMethod = hook.getClass().getMethod("invoke", String.class);
                                    invokeMethod.invoke(hook, EXTENSION_CLASS);
                                    hookedAppInit = true;
                                    System.out.println("[+] Successfully hooked ApplicationInitHook with " + EXTENSION_CLASS);
                                }
                            } catch (Throwable t) {
                                Throwable cause = unwrapReflectionError(t);
                                System.err.println("[!] ApplicationInitHook failed (" + cause.getClass().getName()
                                        + ": " + cause.getMessage() + ")");
                                cause.printStackTrace();
                            }

                            try {
                                Method getOnCreateHook = appInitClass.getMethod("getApplicationInitOnCreateHook");
                                Object onCreateHook = getOnCreateHook.invoke(null);
                                if (onCreateHook == null) {
                                    System.err.println("[!] ApplicationInitHookKt.getApplicationInitOnCreateHook() returned null.");
                                } else {
                                    Method invokeMethod = onCreateHook.getClass().getMethod("invoke", String.class);
                                    invokeMethod.invoke(onCreateHook, EXTENSION_CLASS);
                                    hookedAppOnCreate = true;
                                    System.out.println("[+] Successfully hooked ApplicationInitOnCreateHook with " + EXTENSION_CLASS);
                                }
                            } catch (Throwable t) {
                                Throwable cause = unwrapReflectionError(t);
                                System.err.println("[!] ApplicationInitOnCreateHook failed (" + cause.getClass().getName()
                                        + ": " + cause.getMessage() + ")");
                                cause.printStackTrace();
                            }

                            if (!hookedAppInit && !hookedAppOnCreate) {
                                System.err.println("[!] WARNING: Neither ApplicationInitHook nor ApplicationInitOnCreateHook succeeded. "
                                        + "Extension settings may not pre-register before Settings screen opens.");
                            }
                        } else {
                            System.err.println("[!] ApplicationInitHookKt not found in any ClassLoader! "
                                    + "Ensure official Morphe YouTube patches are loaded in the same patching session.");
                        }
                    } catch (Throwable t) {
                        Throwable cause = unwrapReflectionError(t);
                        System.err.println("[!] Unexpected error hooking ApplicationInitHookKt: "
                                + cause.getClass().getName() + ": " + cause.getMessage());
                        cause.printStackTrace();
                    }

                    // 3. Add independent Settings Screen via official Morphe AddOn Preferences
                    try {
                        File prefsFile = PatchFunctionsKt.resourceFile("morphe_addon_prefs.xml", false);
                        String addonPrefs = "<morphe-add-on-preferences>\n" +
                                "    <screen after=\"morphe_settings_screen_05_misc_sort_by_title\">\n" +
                                "        <PreferenceScreen\n" +
                                "            xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                "            xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                                "            android:key=\"morphe_settings_screen_ryu\"\n" +
                                "            android:title=\"Return YouTube Username+\"\n" +
                                "            android:icon=\"@drawable/morphe_settings_screen_11_misc\"\n" +
                                "            android:layout=\"@layout/preference_with_icon\"\n" +
                                "            app:iconSpaceReserved=\"true\">\n" +
                                "\n" +
                                "            <app.morphe.extension.youtube.patches.returnyoutubeusername.ReturnYouTubeUsernameAboutPreference\n" +
                                "                android:key=\"morphe_ryu_about\"\n" +
                                "                android:title=\"關於 Return YouTube Username+\"\n" +
                                "                android:summary=\"v2.4 · 專案資訊、版本與相關連結\"\n" +
                                "                android:icon=\"@drawable/morphe_settings_screen_00_about\"\n" +
                                "                android:layout=\"@layout/preference_with_icon\"\n" +
                                "                app:iconSpaceReserved=\"true\" />\n" +
                                "\n" +
                                "            <app.morphe.extension.shared.settings.preference.BooleanSettingPreference\n" +
                                "                android:key=\"morphe_ryu_enabled\"\n" +
                                "                android:defaultValue=\"true\"\n" +
                                "                android:title=\"啟用 Return YouTube Username+\"\n" +
                                "                android:summaryOn=\"已開啟\"\n" +
                                "                android:summaryOff=\"已關閉\" />\n" +
                                "\n" +
                                "            <app.morphe.extension.youtube.patches.returnyoutubeusername.DisplayFormatListPreference\n" +
                                "                android:key=\"morphe_ryu_display_format\"\n" +
                                "                android:title=\"顯示格式\"\n" +
                                "                android:dependency=\"morphe_ryu_enabled\" />\n" +
                                "\n" +
                                "            <app.morphe.extension.shared.settings.preference.BooleanSettingPreference\n" +
                                "                android:key=\"morphe_ryu_replace_comments\"\n" +
                                "                android:defaultValue=\"true\"\n" +
                                "                android:dependency=\"morphe_ryu_enabled\"\n" +
                                "                android:title=\"替換留言區使用者名稱\"\n" +
                                "                android:summaryOn=\"替換留言與回覆中之 @代號\"\n" +
                                "                android:summaryOff=\"保留原始 @代號\" />\n" +
                                "\n" +
                                "            <app.morphe.extension.shared.settings.preference.BooleanSettingPreference\n" +
                                "                android:key=\"morphe_ryu_replace_shorts\"\n" +
                                "                android:defaultValue=\"true\"\n" +
                                "                android:dependency=\"morphe_ryu_enabled\"\n" +
                                "                android:title=\"替換 Shorts 上傳者名稱\"\n" +
                                "                android:summaryOn=\"顯示 Shorts 原作者頻道名稱\"\n" +
                                "                android:summaryOff=\"保留原始 @代號\" />\n" +
                                "\n" +
                                "            <app.morphe.extension.shared.settings.preference.BooleanSettingPreference\n" +
                                "                android:key=\"morphe_ryu_replace_live_chat\"\n" +
                                "                android:defaultValue=\"true\"\n" +
                                "                android:dependency=\"morphe_ryu_enabled\"\n" +
                                "                android:title=\"替換直播聊天室使用者名稱\"\n" +
                                "                android:summaryOn=\"替換聊天室與重播中之 @代號\"\n" +
                                "                android:summaryOff=\"保留原始 @代號\" />\n" +
                                "\n" +
                                "            <app.morphe.extension.shared.settings.preference.BooleanSettingPreference\n" +
                                "                android:key=\"morphe_ryu_replace_title_description\"\n" +
                                "                android:defaultValue=\"true\"\n" +
                                "                android:dependency=\"morphe_ryu_enabled\"\n" +
                                "                android:title=\"替換影片標題與說明欄提及\"\n" +
                                "                android:summaryOn=\"將標題與簡介中之 @頻道代碼替換為 @頻道名稱\"\n" +
                                "                android:summaryOff=\"保留原始 @代號\" />\n" +
                                "\n" +
                                "            <app.morphe.extension.shared.settings.preference.ResettableEditTextPreference\n" +
                                "                android:key=\"morphe_ryu_api_key\"\n" +
                                "                android:dependency=\"morphe_ryu_enabled\"\n" +
                                "                android:title=\"YouTube Data API v3 Key (選填)\"\n" +
                                "                android:summary=\"填入個人 API 金鑰以享有更高配額；留空則自動使用公開端點解析\" />\n" +
                                "        </PreferenceScreen>\n" +
                                "    </screen>\n" +
                                "</morphe-add-on-preferences>\n";

                        if (prefsFile.exists()) {
                            String current = new String(Files.readAllBytes(prefsFile.toPath()), StandardCharsets.UTF_8);
                            if (!current.contains("morphe_settings_screen_ryu")) {
                                int closingIdx = current.lastIndexOf("</morphe-add-on-preferences>");
                                if (closingIdx >= 0) {
                                    current = current.substring(0, closingIdx) + addonPrefs.substring(addonPrefs.indexOf("<screen"));
                                    Files.write(prefsFile.toPath(), current.getBytes(StandardCharsets.UTF_8));
                                } else {
                                    System.err.println("[!] Existing morphe_addon_prefs.xml missing closing tag; rewriting file.");
                                    Files.write(prefsFile.toPath(), addonPrefs.getBytes(StandardCharsets.UTF_8));
                                }
                            }
                        } else {
                            Files.write(prefsFile.toPath(), addonPrefs.getBytes(StandardCharsets.UTF_8));
                        }
                        System.out.println("[+] Successfully declared independent Settings Screen via Morphe AddOn Preferences.");
                    } catch (Throwable t) {
                        System.err.println("[!] Failed to declare AddOn Preferences: " + t.getClass().getName() + ": " + t.getMessage());
                        t.printStackTrace();
                    }

                    return Unit.INSTANCE;
                });

                return Unit.INSTANCE;
            }
    );

    private static Throwable unwrapReflectionError(Throwable t) {
        if (t instanceof InvocationTargetException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    private static Class<?> findClass(PatcherContext ctx, String className) {
        // 1. Try Class.forName (standard reflection / Android shared DexClassLoader)
        try {
            return Class.forName(className);
        } catch (Throwable ignored) {}

        // 2. Try current patch's ClassLoader
        try {
            ClassLoader cl = ReturnYouTubeUsernamePatch.class.getClassLoader();
            if (cl != null) {
                return cl.loadClass(className);
            }
        } catch (Throwable ignored) {}

        // 3. Try each patch's ClassLoader from PatcherContext (Morphe CLI on desktop)
        if (ctx != null) {
            Field execField = null;
            Field blockField = null;
            try {
                execField = Patch.class.getDeclaredField("executeBlock");
                execField.setAccessible(true);
            } catch (Throwable ignored) {}

            try {
                for (Patch p : ctx.getAllPatches$morphe_patcher()) {
                    try {
                        Object exec = execField != null ? execField.get(p) : null;
                        if (exec == null) continue;
                        ClassLoader cl = exec.getClass().getClassLoader();
                        if (cl != null) {
                            try {
                                return cl.loadClass(className);
                            } catch (Throwable ignored) {}
                        }
                        if (exec.getClass().getName().contains("ContextFreeBlock")) {
                            if (blockField == null) {
                                blockField = exec.getClass().getDeclaredField("block");
                                blockField.setAccessible(true);
                            }
                            Object inner = blockField.get(exec);
                            if (inner != null && inner.getClass().getClassLoader() != null) {
                                try {
                                    return inner.getClass().getClassLoader().loadClass(className);
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }
}

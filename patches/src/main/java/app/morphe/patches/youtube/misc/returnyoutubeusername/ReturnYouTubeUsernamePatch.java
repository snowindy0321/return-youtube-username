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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import kotlin.Unit;

public class ReturnYouTubeUsernamePatch {

    private static final String EXTENSION_CLASS =
            "Lapp/morphe/extension/youtube/patches/ReturnYouTubeUsernamePatch;";

    public static final Patch patch = PatchKt.patch(
            "Return YouTube Username",
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
                    } catch (Throwable ignored) {}

                    // 1. Hook Litho text components via dynamic reflection
                    try {
                        Class<?> tcpClass = findClass(ctx, "app.morphe.patches.shared.misc.textcomponent.TextComponentPatchKt");

                        if (tcpClass == null) {
                            System.err.println("[!] TextComponentPatchKt not found in any ClassLoader!");
                        } else {
                            Method hookSpannable = tcpClass.getMethod("hookSpannableString", String.class, String.class, boolean.class);
                            Method hookLitho = tcpClass.getMethod("hookLithoSpannableString", String.class, String.class);

                            hookSpannable.invoke(null, EXTENSION_CLASS, "preFetchLithoText", false);
                            hookLitho.invoke(null, EXTENSION_CLASS, "onLithoTextLoaded");
                            System.out.println("[+] Successfully hooked Litho text components via dynamic reflection.");
                        }
                    } catch (Throwable t) {
                        System.err.println("[!] Error hooking Litho text components: " + t);
                        t.printStackTrace();
                    }

                    // 2. Hook Application / Activity init to ensure ReturnYouTubeUsernamePatch is loaded early
                    // This pre-registers all settings before Morphe AbstractPreferenceFragment reads them.
                    try {
                        Class<?> appInitClass = findClass(ctx, "app.morphe.patches.youtube.misc.extension.hooks.ApplicationInitHookKt");
                        if (appInitClass != null) {
                            try {
                                Method getHook = appInitClass.getMethod("getApplicationInitHook");
                                Object hook = getHook.invoke(null);
                                if (hook != null) {
                                    Method invokeMethod = hook.getClass().getMethod("invoke", String.class);
                                    invokeMethod.invoke(hook, EXTENSION_CLASS);
                                    System.out.println("[+] Successfully hooked ApplicationInitHook with " + EXTENSION_CLASS);
                                }
                            } catch (Throwable t) {
                                System.err.println("[!] ApplicationInitHook failed: " + t);
                            }

                            try {
                                Method getOnCreateHook = appInitClass.getMethod("getApplicationInitOnCreateHook");
                                Object onCreateHook = getOnCreateHook.invoke(null);
                                if (onCreateHook != null) {
                                    Method invokeMethod = onCreateHook.getClass().getMethod("invoke", String.class);
                                    invokeMethod.invoke(onCreateHook, EXTENSION_CLASS);
                                    System.out.println("[+] Successfully hooked ApplicationInitOnCreateHook with " + EXTENSION_CLASS);
                                }
                            } catch (Throwable t) {
                                System.err.println("[!] ApplicationInitOnCreateHook failed: " + t);
                            }
                        } else {
                            System.err.println("[!] ApplicationInitHookKt not found in any ClassLoader!");
                        }
                    } catch (Throwable t) {
                        System.err.println("[!] Error hooking ApplicationInit: " + t);
                    }

                    // 3. Add independent Settings Screen via official Morphe AddOn Preferences
                    try {
                        File prefsFile = PatchFunctionsKt.resourceFile("morphe_addon_prefs.xml", false);
                        String addonPrefs = "<morphe-add-on-preferences>\n" +
                                "    <screen after=\"morphe_settings_screen_11_misc_sort_by_title\">\n" +
                                "        <PreferenceScreen\n" +
                                "            xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                "            xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                                "            android:key=\"morphe_settings_screen_return_youtube_username\"\n" +
                                "            android:title=\"Return YouTube Username\"\n" +
                                "            android:icon=\"@drawable/morphe_settings_screen_11_misc\"\n" +
                                "            android:layout=\"@layout/preference_with_icon\"\n" +
                                "            app:iconSpaceReserved=\"true\">\n" +
                                "\n" +
                                "            <SwitchPreference\n" +
                                "                android:key=\"morphe_return_youtube_username_enabled\"\n" +
                                "                android:defaultValue=\"true\"\n" +
                                "                android:title=\"啟用 Return YouTube Username\"\n" +
                                "                android:summaryOn=\"已開啟\"\n" +
                                "                android:summaryOff=\"已關閉\" />\n" +
                                "\n" +
                                "            <SwitchPreference\n" +
                                "                android:key=\"morphe_return_youtube_username_replace_comments\"\n" +
                                "                android:defaultValue=\"true\"\n" +
                                "                android:dependency=\"morphe_return_youtube_username_enabled\"\n" +
                                "                android:title=\"替換留言區使用者名稱\"\n" +
                                "                android:summaryOn=\"替換留言與回覆中之 @代號\"\n" +
                                "                android:summaryOff=\"保留原始 @代號\" />\n" +
                                "\n" +
                                "            <SwitchPreference\n" +
                                "                android:key=\"morphe_return_youtube_username_replace_shorts\"\n" +
                                "                android:defaultValue=\"true\"\n" +
                                "                android:dependency=\"morphe_return_youtube_username_enabled\"\n" +
                                "                android:title=\"替換 Shorts 上傳者名稱\"\n" +
                                "                android:summaryOn=\"顯示 Shorts 原作者頻道名稱\"\n" +
                                "                android:summaryOff=\"保留原始 @代號\" />\n" +
                                "\n" +
                                "            <SwitchPreference\n" +
                                "                android:key=\"morphe_return_youtube_username_replace_live_chat\"\n" +
                                "                android:defaultValue=\"true\"\n" +
                                "                android:dependency=\"morphe_return_youtube_username_enabled\"\n" +
                                "                android:title=\"替換直播聊天室使用者名稱\"\n" +
                                "                android:summaryOn=\"替換聊天室與重播中之 @代號\"\n" +
                                "                android:summaryOff=\"保留原始 @代號\" />\n" +
                                "\n" +
                                "            <SwitchPreference\n" +
                                "                android:key=\"morphe_return_youtube_username_replace_title_description\"\n" +
                                "                android:defaultValue=\"true\"\n" +
                                "                android:dependency=\"morphe_return_youtube_username_enabled\"\n" +
                                "                android:title=\"替換影片標題與說明欄提及\"\n" +
                                "                android:summaryOn=\"將標題與簡介中之 @頻道代碼替換為 @頻道名稱\"\n" +
                                "                android:summaryOff=\"保留原始 @代號\" />\n" +
                                "\n" +
                                "            <EditTextPreference\n" +
                                "                android:key=\"morphe_return_youtube_username_api_key\"\n" +
                                "                android:dependency=\"morphe_return_youtube_username_enabled\"\n" +
                                "                android:title=\"YouTube Data API v3 Key (選填)\"\n" +
                                "                android:summary=\"填入個人 API 金鑰以享有更高配額；留空則自動使用公開端點解析\" />\n" +
                                "        </PreferenceScreen>\n" +
                                "    </screen>\n" +
                                "</morphe-add-on-preferences>\n";

                        if (prefsFile.exists()) {
                            String current = new String(Files.readAllBytes(prefsFile.toPath()), StandardCharsets.UTF_8);
                            current = current.replace("</morphe-add-on-preferences>", addonPrefs.substring(addonPrefs.indexOf("<screen")));
                            Files.write(prefsFile.toPath(), current.getBytes(StandardCharsets.UTF_8));
                        } else {
                            Files.write(prefsFile.toPath(), addonPrefs.getBytes(StandardCharsets.UTF_8));
                        }
                        System.out.println("[+] Successfully declared independent Settings Screen via Morphe AddOn Preferences.");
                    } catch (Throwable t) {
                        System.err.println("[!] Failed to declare AddOn Preferences: " + t);
                    }

                    return Unit.INSTANCE;
                });

                return Unit.INSTANCE;
            }
    );

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

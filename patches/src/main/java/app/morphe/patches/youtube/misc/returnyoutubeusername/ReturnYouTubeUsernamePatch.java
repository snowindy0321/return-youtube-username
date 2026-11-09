package app.morphe.patches.youtube.misc.returnyoutubeusername;

import app.morphe.patcher.PatcherContext;
import app.morphe.patcher.patch.AppTarget;
import app.morphe.patcher.patch.Compatibility;
import app.morphe.patcher.patch.Patch;
import app.morphe.patcher.patch.PatchFunctionsKt;
import app.morphe.patcher.patch.PatchKt;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
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

                def.extendWith(() -> ReturnYouTubeUsernamePatch.class.getClassLoader().getResourceAsStream("extensions/youtube.mpe"));

                def.execute(() -> {
                    System.out.println("[*] ReturnYouTubeUsernamePatch executing...");
                    PatcherContext ctx = null;
                    try {
                        Field f = PatcherContext.class.getDeclaredField("current");
                        f.setAccessible(true);
                        ctx = (PatcherContext) f.get(null);
                    } catch (Exception ignored) {}

                    ClassLoader officialCl = findOfficialClassLoader(ctx);

                    if (officialCl == null) {
                        System.err.println("[!] Official ClassLoader not found! Make sure official Morphe patches are loaded.");
                        return Unit.INSTANCE;
                    }

                    try {
                        // 1. Hook Litho text components
                        Class<?> tcpClass = officialCl.loadClass("app.morphe.patches.shared.misc.textcomponent.TextComponentPatchKt");
                        Method hookSpannable = tcpClass.getMethod("hookSpannableString", String.class, String.class, boolean.class);
                        Method hookLitho = tcpClass.getMethod("hookLithoSpannableString", String.class, String.class);

                        hookSpannable.invoke(null, EXTENSION_CLASS, "preFetchLithoText", false);
                        hookLitho.invoke(null, EXTENSION_CLASS, "onLithoTextLoaded");
                        System.out.println("[+] Successfully hooked Litho text components via dynamic reflection.");

                        // 2. Add independent Settings Screen via official Morphe AddOn Preferences
                        try {
                            File prefsFile = PatchFunctionsKt.resourceFile("morphe_addon_prefs.xml", false);
                            String addonPrefs = "<morphe-add-on-preferences>\n" +
                                    "    <screen after=\"morphe_settings_screen_11_misc_sort_by_title\">\n" +
                                    "        <PreferenceScreen\n" +
                                    "            xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                    "            xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                                    "            android:key=\"morphe_settings_screen_return_youtube_username\"\n" +
                                    "            android:title=\"Return YouTube Username\"\n" +
                                    "            android:summary=\"還原留言區與 Shorts 之使用者名稱 (@handle) 為頻道名稱\"\n" +
                                    "            android:icon=\"@drawable/morphe_settings_screen_11_misc\"\n" +
                                    "            app:icon=\"@drawable/morphe_settings_screen_11_misc\"\n" +
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
                                    "                android:title=\"替換留言區使用者名稱\"\n" +
                                    "                android:summaryOn=\"替換留言與回覆中之 @代號\"\n" +
                                    "                android:summaryOff=\"保留原始 @代號\" />\n" +
                                    "\n" +
                                    "            <SwitchPreference\n" +
                                    "                android:key=\"morphe_return_youtube_username_replace_shorts\"\n" +
                                    "                android:defaultValue=\"true\"\n" +
                                    "                android:title=\"替換 Shorts 上傳者名稱\"\n" +
                                    "                android:summaryOn=\"顯示 Shorts 原作者頻道名稱\"\n" +
                                    "                android:summaryOff=\"保留原始 @代號\" />\n" +
                                    "\n" +
                                    "            <SwitchPreference\n" +
                                    "                android:key=\"morphe_return_youtube_username_replace_all_handles\"\n" +
                                    "                android:defaultValue=\"true\"\n" +
                                    "                android:title=\"替換全域所有 @代號\"\n" +
                                    "                android:summaryOn=\"包含社群貼文、提及等所有可解析之 Handle\"\n" +
                                    "                android:summaryOff=\"僅替換留言與 Shorts\" />\n" +
                                    "\n" +
                                    "            <EditTextPreference\n" +
                                    "                android:key=\"morphe_return_youtube_username_api_key\"\n" +
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
                        } catch (Exception e) {
                            System.err.println("[!] Failed to declare AddOn Preferences: " + e);
                        }

                    } catch (Exception e) {
                        System.err.println("[!] Error executing ReturnYouTubeUsernamePatch: " + e);
                        e.printStackTrace();
                    }

                    return Unit.INSTANCE;
                });

                return Unit.INSTANCE;
            }
    );

    private static ClassLoader findOfficialClassLoader(PatcherContext ctx) {
        if (ctx == null) return null;
        Field execField = null;
        Field blockField = null;
        try {
            execField = Patch.class.getDeclaredField("executeBlock");
            execField.setAccessible(true);
        } catch (Exception ignored) {}

        for (Patch p : ctx.getAllPatches$morphe_patcher()) {
            try {
                Object exec = execField.get(p);
                if (exec == null) continue;
                ClassLoader cl = exec.getClass().getClassLoader();
                if (cl instanceof URLClassLoader && cl != ReturnYouTubeUsernamePatch.class.getClassLoader()) {
                    return cl;
                }
                if (exec.getClass().getName().equals("app.morphe.patcher.patch.ContextFreeBlock")) {
                    if (blockField == null) {
                        blockField = exec.getClass().getDeclaredField("block");
                        blockField.setAccessible(true);
                    }
                    Object inner = blockField.get(exec);
                    if (inner != null && (inner.getClass().getClassLoader() instanceof URLClassLoader)
                            && inner.getClass().getClassLoader() != ReturnYouTubeUsernamePatch.class.getClassLoader()) {
                        return inner.getClass().getClassLoader();
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}

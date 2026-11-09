package app.morphe.patches.youtube.misc.returnyoutubeusername;

import app.morphe.patcher.PatcherContext;
import app.morphe.patcher.patch.AppTarget;
import app.morphe.patcher.patch.Compatibility;
import app.morphe.patcher.patch.Patch;
import app.morphe.patcher.patch.PatchKt;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.Set;
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

                        // 2. Add independent Settings Screen
                        Class<?> prefScreenClass = officialCl.loadClass("app.morphe.patches.youtube.misc.settings.PreferenceScreen");
                        Object prefScreenInstance = prefScreenClass.getField("INSTANCE").get(null);

                        Class<?> switchPrefClass = officialCl.loadClass("app.morphe.patches.shared.misc.settings.preference.SwitchPreference");
                        Class<?> listPrefClass = officialCl.loadClass("app.morphe.patches.shared.misc.settings.preference.ListPreference");
                        Class<?> textPrefClass = officialCl.loadClass("app.morphe.patches.shared.misc.settings.preference.TextPreference");
                        Class<?> inputTypeClass = officialCl.loadClass("app.morphe.patches.shared.misc.settings.preference.InputType");
                        Class<?> sortingClass = officialCl.loadClass("app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference$Sorting");
                        Object unsorted = sortingClass.getField("UNSORTED").get(null);
                        Object inputTypeText = inputTypeClass.getField("TEXT").get(null);

                        Set<Object> subPreferences = new LinkedHashSet<>();

                        Constructor<?> switchCtor = switchPrefClass.getConstructor(
                                String.class, String.class, boolean.class, String.class, String.class, String.class, String.class
                        );
                        subPreferences.add(switchCtor.newInstance(
                                "morphe_return_youtube_username_enabled",
                                "morphe_return_youtube_username_enabled_title",
                                true,
                                "SwitchPreference",
                                null, null, null
                        ));

                        Constructor<?> listCtor = listPrefClass.getConstructor(
                                String.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class
                        );
                        subPreferences.add(listCtor.newInstance(
                                "morphe_return_youtube_username_display_format",
                                "morphe_return_youtube_username_display_format_title",
                                null, null, null,
                                "app.morphe.extension.shared.settings.preference.CustomDialogListPreference",
                                "morphe_return_youtube_username_display_format_entries",
                                "morphe_return_youtube_username_display_format_entry_values"
                        ));

                        subPreferences.add(switchCtor.newInstance(
                                "morphe_return_youtube_username_replace_comments",
                                "morphe_return_youtube_username_replace_comments_title",
                                true,
                                "SwitchPreference",
                                null, null, null
                        ));

                        subPreferences.add(switchCtor.newInstance(
                                "morphe_return_youtube_username_replace_shorts",
                                "morphe_return_youtube_username_replace_shorts_title",
                                true,
                                "SwitchPreference",
                                null, null, null
                        ));

                        subPreferences.add(switchCtor.newInstance(
                                "morphe_return_youtube_username_replace_all_handles",
                                "morphe_return_youtube_username_replace_all_handles_title",
                                true,
                                "SwitchPreference",
                                null, null, null
                        ));

                        Constructor<?> textCtor = textPrefClass.getConstructor(
                                String.class, String.class, String.class, String.class, String.class, String.class, String.class, inputTypeClass
                        );
                        subPreferences.add(textCtor.newInstance(
                                "morphe_return_youtube_username_api_key",
                                "morphe_return_youtube_username_api_key_title",
                                "morphe_return_youtube_username_api_key_summary",
                                null, null, null,
                                "app.morphe.extension.shared.settings.preference.ResettableEditTextPreference",
                                inputTypeText
                        ));

                        Class<?> screenClass = officialCl.loadClass("app.morphe.patches.shared.misc.settings.preference.BasePreferenceScreen$Screen");
                        Class<?> basePrefScreenClass = officialCl.loadClass("app.morphe.patches.shared.misc.settings.preference.BasePreferenceScreen");

                        Constructor<?> screenCtor = screenClass.getConstructor(
                                basePrefScreenClass,
                                String.class, String.class, String.class, String.class, String.class, String.class,
                                Set.class, Set.class, sortingClass
                        );
                        screenCtor.newInstance(
                                prefScreenInstance,
                                "morphe_settings_screen_return_youtube_username",
                                "morphe_return_youtube_username_screen_title",
                                "morphe_return_youtube_username_screen_summary",
                                "@drawable/morphe_settings_screen_11_misc",
                                "@drawable/morphe_settings_screen_11_misc_bold",
                                "@layout/preference_with_icon",
                                subPreferences,
                                new LinkedHashSet<>(),
                                unsorted
                        );
                        System.out.println("[+] Successfully registered independent Settings Screen via dynamic reflection.");

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

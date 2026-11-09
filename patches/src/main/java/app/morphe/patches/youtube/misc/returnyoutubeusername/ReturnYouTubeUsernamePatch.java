package app.morphe.patches.youtube.misc.returnyoutubeusername;

import app.morphe.patcher.patch.Patch;
import app.morphe.patcher.patch.PatchKt;
import app.morphe.patches.shared.misc.settings.preference.BasePreference;
import app.morphe.patches.shared.misc.settings.preference.BasePreferenceScreen;
import app.morphe.patches.shared.misc.settings.preference.InputType;
import app.morphe.patches.shared.misc.settings.preference.ListPreference;
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference;
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference.Sorting;
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference;
import app.morphe.patches.shared.misc.settings.preference.TextPreference;
import app.morphe.patches.shared.misc.textcomponent.TextComponentPatchKt;
import app.morphe.patches.youtube.misc.extension.SharedExtensionPatchKt;
import app.morphe.patches.youtube.misc.settings.PreferenceScreen;
import app.morphe.patches.youtube.misc.settings.SettingsPatchKt;
import app.morphe.patches.youtube.shared.Constants;

import java.util.LinkedHashSet;
import java.util.Set;
import kotlin.Unit;

@SuppressWarnings("unused")
public class ReturnYouTubeUsernamePatch {

    private static final String EXTENSION_CLASS =
            "Lapp/morphe/extension/youtube/patches/ReturnYouTubeUsernamePatch;";

    public static final Patch patch = PatchKt.patch(
            "Return YouTube Username",
            "Adds an option to replace YouTube handles (@handle) with original channel names in comments, Shorts uploaders, and throughout the app.",
            true, // enabled by default
            def -> {
                def.dependsOn(
                        SettingsPatchKt.getSettingsPatch(),
                        SharedExtensionPatchKt.getSharedExtensionPatch(),
                        TextComponentPatchKt.getTextComponentPatch(),
                        TextComponentPatchKt.getLithoSpannableStringPatch()
                );

                def.compatibleWith(Constants.INSTANCE.getCOMPATIBILITY_YOUTUBE());

                def.execute(() -> {
                    Set<BasePreference> subPreferences = new LinkedHashSet<>();
                    subPreferences.add(new SwitchPreference(
                            "morphe_return_youtube_username_enabled",
                            "morphe_return_youtube_username_enabled_title",
                            true, // summary
                            "SwitchPreference",
                            null, null, null
                    ));
                    subPreferences.add(new ListPreference(
                            "morphe_return_youtube_username_display_format",
                            "morphe_return_youtube_username_display_format_title",
                            null, null, null,
                            "app.morphe.extension.shared.settings.preference.CustomDialogListPreference",
                            "morphe_return_youtube_username_display_format_entries",
                            "morphe_return_youtube_username_display_format_entry_values"
                    ));
                    subPreferences.add(new SwitchPreference(
                            "morphe_return_youtube_username_replace_comments",
                            "morphe_return_youtube_username_replace_comments_title",
                            true, // summary
                            "SwitchPreference",
                            null, null, null
                    ));
                    subPreferences.add(new SwitchPreference(
                            "morphe_return_youtube_username_replace_shorts",
                            "morphe_return_youtube_username_replace_shorts_title",
                            true, // summary
                            "SwitchPreference",
                            null, null, null
                    ));
                    subPreferences.add(new SwitchPreference(
                            "morphe_return_youtube_username_replace_all_handles",
                            "morphe_return_youtube_username_replace_all_handles_title",
                            true, // summary
                            "SwitchPreference",
                            null, null, null
                    ));
                    subPreferences.add(new TextPreference(
                            "morphe_return_youtube_username_api_key",
                            "morphe_return_youtube_username_api_key_title",
                            "morphe_return_youtube_username_api_key_summary",
                            null, null, null,
                            "app.morphe.extension.shared.settings.preference.ResettableEditTextPreference",
                            InputType.TEXT
                    ));

                    // Create an independent top-level settings screen in Morphe settings
                    // completely separated from official MISC (其他) or other screens.
                    PreferenceScreen.INSTANCE.new Screen(
                            "morphe_settings_screen_return_youtube_username",
                            "morphe_return_youtube_username_screen_title",
                            "morphe_return_youtube_username_screen_summary",
                            "@drawable/morphe_settings_screen_11_misc",
                            "@drawable/morphe_settings_screen_11_misc_bold",
                            "@layout/preference_with_icon",
                            subPreferences,
                            new LinkedHashSet<>(),
                            Sorting.UNSORTED
                    );

                    // Background thread pre-fetch during SpannableString building
                    TextComponentPatchKt.hookSpannableString(EXTENSION_CLASS, "preFetchLithoText", false);

                    // Litho text rendering & cache lookup interceptor
                    TextComponentPatchKt.hookLithoSpannableString(EXTENSION_CLASS, "onLithoTextLoaded");

                    return Unit.INSTANCE;
                });

                return Unit.INSTANCE;
            }
    );
}

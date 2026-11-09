package com.divinedoll.launcher.fragments;

import static com.divinedoll.launcher.Tools.openPath;
import static com.divinedoll.launcher.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import com.divinedoll.launcher.CustomControlsActivity;
import com.divinedoll.launcher.R;

import com.divinedoll.launcher.Tools;
import com.divinedoll.launcher.contracts.OpenDocumentWithExtension;
import com.divinedoll.launcher.extra.ExtraConstants;
import com.divinedoll.launcher.extra.ExtraCore;
import com.divinedoll.launcher.instances.Instance;
import com.divinedoll.launcher.instances.Instances;
import com.divinedoll.launcher.progresskeeper.ProgressKeeper;
import com.divinedoll.launcher.utils.BannerManager;
import com.divinedoll.launcher.utils.FileUtils;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    private ImageView mBannerView;
    private View mBannerTitle;

    private final ActivityResultLauncher<String> mBannerPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), (Uri uri) -> {
                if(uri == null) return;
                try {
                    BannerManager.saveCustom(requireContext(), uri);
                    refreshBanner();
                } catch (Exception e) {
                    Toast.makeText(requireContext(), R.string.fork_banner_error, Toast.LENGTH_LONG).show();
                }
            });

    private void refreshBanner() {
        if(mBannerView == null) return;
        boolean custom = BannerManager.apply(requireContext(), mBannerView);
        if(mBannerTitle != null) BannerManager.setTitleVisible(mBannerTitle, custom);
    }

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.setBackgroundColor(com.divinedoll.launcher.utils.ThemeColors.background());
        View sidebar = view.findViewById(R.id.sidebar_scroll);
        if(sidebar != null) sidebar.setBackgroundColor(com.divinedoll.launcher.utils.ThemeColors.sidebar());
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mModsButton = view.findViewById(R.id.mods_button);
        Button mLanButton = view.findViewById(R.id.lan_button);
        Button mBedrockButton = view.findViewById(R.id.bedrock_button);
        Button mAppearanceButton = view.findViewById(R.id.appearance_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        com.divinedoll.launcher.utils.McStyle.headingText(mPlayButton);
        mPlayButton.setBackground(com.divinedoll.launcher.utils.PlayColor.background(requireContext()));
        mPlayButton.setOnLongClickListener(v -> {
            com.divinedoll.launcher.utils.PlayColor.choose(requireContext(), () ->
                    mPlayButton.setBackground(com.divinedoll.launcher.utils.PlayColor.background(requireContext())));
            return true;
        });
        buildHomeCard(view);
        mBannerView = view.findViewById(R.id.bedrock_banner);
        mBannerTitle = view.findViewById(R.id.bedrock_banner_title);
        if(mBannerTitle instanceof TextView) ((TextView) mBannerTitle).setText(com.divinedoll.launcher.utils.AppearancePanel.launcherName(requireContext()));
        refreshBanner();
        mBannerView.setOnClickListener(v -> BannerManager.showChooser(requireContext(),
                () -> mBannerPicker.launch("image/*"), this::refreshBanner));
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        TextView tipView = view.findViewById(R.id.launcher_tip);
        if(tipView != null) {
            String[] tips = getResources().getStringArray(R.array.fork_tips);
            int[] index = {new java.util.Random().nextInt(tips.length)};
            tipView.setText(tips[index[0]]);
            tipView.setOnClickListener(v -> {
                index[0] = (index[0] + 1) % tips.length;
                tipView.setText(tips[index[0]]);
            });
        }

        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        mModsButton.setOnClickListener(v -> {
            Bundle modsArgs = new Bundle();
            modsArgs.putBoolean(SearchModFragment.ARG_MODPACK, false);
            Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, modsArgs);
        });
        mBedrockButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), BedrockFragment.class, BedrockFragment.TAG, null));
        // Skins, Installations and Patch notes are tabs in the header
        ViewGroup tabsContainer = view.findViewById(R.id.launcher_tabs_container);
        if(tabsContainer != null) {
            tabsContainer.addView(com.divinedoll.launcher.utils.ForkUi.pageHeader(requireContext(), requireActivity(),
                    com.divinedoll.launcher.utils.ForkUi.TAB_PLAY), new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        Button javaButton = view.findViewById(R.id.java_button);
        if(javaButton != null) javaButton.setBackgroundColor(0xFF343434);
        mAppearanceButton.setOnClickListener(v -> showAppearanceDialog(mPlayButton));
        mLanButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), LanFragment.class, LanFragment.TAG, null));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> openGameDirectory(v.getContext()));


        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
    }

    private TextView mHomeName, mHomeInfo, mHomeWorld;
    private android.widget.ImageView mHomeIcon;

    /** Wide panel over the bottom of the banner: icon, the instance that will be played, play time, last world and quick actions. */
    private void buildHomeCard(View root) {
        if(!(root instanceof androidx.constraintlayout.widget.ConstraintLayout)) return;
        Context c = requireContext();
        android.widget.LinearLayout card = new android.widget.LinearLayout(c);
        card.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = com.divinedoll.launcher.utils.ForkUi.dp(c, 8);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(com.divinedoll.launcher.utils.ForkUi.box(c, 0xCC000000, com.divinedoll.launcher.utils.AccentColor.get(), 2));

        mHomeIcon = new android.widget.ImageView(c);
        mHomeIcon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        card.addView(mHomeIcon, new android.widget.LinearLayout.LayoutParams(
                com.divinedoll.launcher.utils.ForkUi.dp(c, 44), com.divinedoll.launcher.utils.ForkUi.dp(c, 44)));

        android.widget.LinearLayout texts = new android.widget.LinearLayout(c);
        texts.setOrientation(android.widget.LinearLayout.VERTICAL);
        texts.setPadding(com.divinedoll.launcher.utils.ForkUi.dp(c, 10), 0, com.divinedoll.launcher.utils.ForkUi.dp(c, 8), 0);
        mHomeName = com.divinedoll.launcher.utils.ForkUi.text(c, "", 15, true);
        mHomeName.setSingleLine(true);
        mHomeName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(mHomeName);
        mHomeInfo = com.divinedoll.launcher.utils.ForkUi.text(c, "", 11, false);
        mHomeInfo.setTextColor(0xFFCCCCCC);
        mHomeInfo.setSingleLine(true);
        mHomeInfo.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(mHomeInfo);
        mHomeWorld = com.divinedoll.launcher.utils.ForkUi.text(c, "", 10, false);
        mHomeWorld.setTextColor(com.divinedoll.launcher.utils.AccentColor.light());
        mHomeWorld.setSingleLine(true);
        mHomeWorld.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(mHomeWorld);
        card.addView(texts, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int[] labels = {R.string.fork_home_files, R.string.fork_home_logs, R.string.fork_home_edit};
        for(int i = 0; i < labels.length; i++) {
            final int which = i;
            Button b = com.divinedoll.launcher.utils.ForkUi.button(c, getString(labels[i]), false);
            b.setTextSize(11);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight(com.divinedoll.launcher.utils.ForkUi.dp(c, 36));
            b.setMinimumHeight(com.divinedoll.launcher.utils.ForkUi.dp(c, 36));
            b.setPadding(com.divinedoll.launcher.utils.ForkUi.dp(c, 10), 0, com.divinedoll.launcher.utils.ForkUi.dp(c, 10), 0);
            b.setOnClickListener(v -> onHomeAction(which));
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = com.divinedoll.launcher.utils.ForkUi.dp(c, 4);
            card.addView(b, lp);
        }

        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.startToStart = R.id.bedrock_banner;
        params.endToEnd = R.id.bedrock_banner;
        params.bottomToTop = R.id.launcher_tip;
        params.setMarginStart(com.divinedoll.launcher.utils.ForkUi.dp(c, 12));
        params.setMarginEnd(com.divinedoll.launcher.utils.ForkUi.dp(c, 12));
        params.bottomMargin = com.divinedoll.launcher.utils.ForkUi.dp(c, 4);
        ((ViewGroup) root).addView(card, params);
        refreshHomeCard();
    }

    private static String lastWorld(java.io.File gameDir) {
        java.io.File[] worlds = new java.io.File(gameDir, "saves").listFiles(java.io.File::isDirectory);
        if(worlds == null || worlds.length == 0) return null;
        java.io.File newest = worlds[0];
        for(java.io.File w : worlds) if(w.lastModified() > newest.lastModified()) newest = w;
        return newest.getName();
    }

    private void refreshHomeCard() {
        if(mHomeName == null || getContext() == null) return;
        Context c = requireContext();
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            mHomeName.setText(R.string.fork_home_no_instance);
            mHomeInfo.setText("");
            mHomeWorld.setText("");
            mHomeIcon.setImageBitmap(null);
            return;
        }
        mHomeIcon.setImageBitmap(InstallationsFragment.iconOf(c, instance));
        mHomeName.setText(String.valueOf(instance.name));
        mHomeInfo.setText(instance.versionId + "  ·  " + com.divinedoll.launcher.utils.InstanceTools.stats(c, String.valueOf(instance.name)));
        String world = lastWorld(instance.getGameDirectory());
        mHomeWorld.setText(world == null ? "" : getString(R.string.fork_home_world, world));
    }

    private void onHomeAction(int which) {
        Context c = requireContext();
        Instance instance = Instances.loadSelectedInstance();
        if(which == 1) {
            shareLog(c);
        } else if(instance == null) {
            Toast.makeText(c, R.string.no_instance, Toast.LENGTH_LONG).show();
        } else if(which == 0) {
            com.divinedoll.launcher.utils.InstanceFiles.show(requireActivity(), instance.getGameDirectory(), instance.versionId);
        } else {
            Tools.swapFragment(requireActivity(), InstanceEditorFragment.class, InstanceEditorFragment.TAG, null);
        }
    }

    private static final String BEDROCK_PACKAGE = "com.mojang.minecraftpe";

    private boolean isBedrockInstalled(Context context) {
        return context.getPackageManager().getLaunchIntentForPackage(BEDROCK_PACKAGE) != null;
    }

    /** Shows "Installed" or "Not installed" under the Bedrock button. */
    private void updateBedrockStatus() {
        View root = getView();
        Context context = getContext();
        if(root == null || context == null) return;
        Button button = root.findViewById(R.id.bedrock_button);
        if(button == null) return;
        boolean installed = isBedrockInstalled(context);
        String title = getString(R.string.fork_bedrock_button);
        String status = getString(installed ? R.string.fork_bedrock_installed : R.string.fork_bedrock_not_installed);
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(title + "\n" + status);
        int start = title.length() + 1;
        text.setSpan(new android.text.style.RelativeSizeSpan(0.75f), start, text.length(), 0);
        text.setSpan(new android.text.style.ForegroundColorSpan(installed ? 0xFF7CD65A : 0xFFE08A5A), start, text.length(), 0);
        button.setText(text);
    }

    /** Bedrock is a separate app: open it if installed, otherwise offer its store page. */
    private void openBedrock() {
        Context context = requireContext();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(BEDROCK_PACKAGE);
        if(launch != null) {
            try {
                startActivity(launch);
            } catch (Exception e) {
                Toast.makeText(context, R.string.fork_bedrock_missing, Toast.LENGTH_LONG).show();
            }
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(R.string.fork_bedrock_button)
                .setMessage(R.string.fork_bedrock_missing)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.fork_bedrock_open_store, (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + BEDROCK_PACKAGE)));
                    } catch (Exception e) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=" + BEDROCK_PACKAGE)));
                        } catch (Exception ignored) {
                            Toast.makeText(context, R.string.fork_bedrock_missing, Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .show();
    }

    private final androidx.activity.result.ActivityResultLauncher<String> mThemePicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                if(uri == null) return;
                boolean ok = com.divinedoll.launcher.utils.ThemeManager.importFrom(requireContext(), uri);
                Toast.makeText(requireContext(), ok ? R.string.fork_theme_imported : R.string.fork_theme_error, Toast.LENGTH_LONG).show();
                if(ok) requireActivity().recreate();
            });

    private void showAppearanceDialog(Button playButton) {
        Context c = requireContext();
        com.divinedoll.launcher.utils.AppearancePanel.show(requireActivity(), new com.divinedoll.launcher.utils.AppearancePanel.Hooks() {
            @Override public void pickBanner() {
                BannerManager.showChooser(c, () -> mBannerPicker.launch("image/*"), MainMenuFragment.this::refreshBanner);
            }
            @Override public void refreshBanner() {
                MainMenuFragment.this.refreshBanner();
            }
            @Override public void refreshPlay() {
                playButton.setBackground(com.divinedoll.launcher.utils.PlayColor.background(c));
            }
            @Override public void pickThemeFile() {
                mThemePicker.launch("*/*");
            }
            @Override public void recreate() {
                requireActivity().recreate();
            }
        });
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHomeCard();
        updateBedrockStatus();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}

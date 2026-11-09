package com.divinedoll.launcher.utils;

import android.app.Activity;
import android.text.format.Formatter;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipOutputStream;

import com.divinedoll.launcher.R;

/**
 * Instance manager: only the user content folders of an instance (mods, config, worlds...).
 * It never opens the game installation (versions, libraries, runtime) and every path is checked
 * to stay inside the instance folder. Deleting sends items to a trash that can be restored.
 */
public final class InstanceFiles {
    private InstanceFiles() {}

    private static final String[] FOLDERS = {"mods", "config", "resourcepacks", "shaderpacks", "saves", "screenshots", "logs", "crash-reports", "backups", "trash"};
    private static final int[] LABELS = {R.string.fork_files_mods, R.string.fork_files_config, R.string.fork_files_resourcepacks,
            R.string.fork_files_shaderpacks, R.string.fork_files_saves, R.string.fork_files_screenshots, R.string.fork_files_logs, R.string.fork_files_crash,
            R.string.fork_files_backups, R.string.fork_files_trash};
    private static final int MODS = 0, SAVES = 4, TRASH = 9, PERMANENT_FROM = 6;

    public static void show(Activity act, File gameDir, String versionId) {
        if(gameDir == null) return;
        String[] names = new String[FOLDERS.length];
        for(int i = 0; i < names.length; i++) {
            String[] kids = new File(gameDir, FOLDERS[i]).list();
            names[i] = act.getString(LABELS[i]) + "  (" + (kids == null ? 0 : kids.length) + ")";
        }
        new AlertDialog.Builder(act)
                .setTitle(R.string.fork_files_title)
                .setItems(names, (d, which) -> showFolder(act, gameDir, versionId, which))
                .setNegativeButton(R.string.fork_files_back, null)
                .show();
    }

    private static boolean inside(File parent, File child) {
        try {
            return child.getCanonicalPath().startsWith(parent.getCanonicalPath() + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private static long sizeOf(File f) {
        if(!f.isDirectory()) return f.length();
        long total = 0;
        File[] list = f.listFiles();
        if(list != null) for(File c : list) total += sizeOf(c);
        return total;
    }

    private static boolean deleteTree(File f) {
        if(f.isDirectory() && !java.nio.file.Files.isSymbolicLink(f.toPath())) {
            File[] list = f.listFiles();
            if(list != null) for(File c : list) deleteTree(c);
        }
        return f.delete();
    }

    private static void sortByName(List<File> files) {
        java.util.Collections.sort(files, (a, c) -> a.getName().compareToIgnoreCase(c.getName()));
    }

    private static Button action(Activity act, LinearLayout row, int label, android.view.View.OnClickListener l) {
        Button b = ForkUi.button(act, act.getString(label), false);
        b.setTextSize(12);
        b.setOnClickListener(l);
        row.addView(b, ForkUi.weight(1f));
        return b;
    }

    private static void showFolder(Activity act, File gameDir, String versionId, int index) {
        File folder = new File(gameDir, FOLDERS[index]);
        if(folder.exists() && !inside(gameDir, folder)) return;

        List<File> files = new ArrayList<>();
        File[] raw = folder.listFiles();
        if(raw != null) for(File f : raw) if(inside(folder, f)) files.add(f);
        sortByName(files);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(act, android.R.layout.simple_list_item_multiple_choice, new ArrayList<>());
        ListView list = new ListView(act);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        list.setAdapter(adapter);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = ForkUi.dp(act, 12);
        root.setPadding(pad, pad / 2, pad, 0);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ForkUi.dp(act, 240)));

        AlertDialog.Builder b = new AlertDialog.Builder(act).setTitle(LABELS[index]).setView(root);
        b.setNegativeButton(R.string.fork_files_back, (d, w) -> show(act, gameDir, versionId));
        b.setNeutralButton(R.string.fork_files_all, null);
        b.setPositiveButton(R.string.fork_files_delete, null);
        AlertDialog dialog = b.create();
        dialog.show();

        Runnable refresh = () -> {
            adapter.clear();
            long total = 0;
            for(File f : files) {
                long size = sizeOf(f);
                total += size;
                String label = (f.getName().endsWith(".disabled") ? "[OFF] " : "") + f.getName() + (f.isDirectory() ? "/" : "");
                adapter.add(label + "  —  " + Formatter.formatShortFileSize(act, size));
            }
            if(files.isEmpty()) adapter.add(act.getString(R.string.fork_files_empty));
            adapter.notifyDataSetChanged();
            dialog.setTitle(act.getString(LABELS[index]) + " · " + Formatter.formatShortFileSize(act, total));
        };
        refresh.run();

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            boolean any = false;
            for(int i = 0; i < files.size(); i++) if(!list.isItemChecked(i)) any = true;
            for(int i = 0; i < files.size(); i++) list.setItemChecked(i, any);
        });

        boolean permanent = index >= PERMANENT_FROM;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<File> chosen = chosen(list, files);
            if(chosen.isEmpty()) return;
            new AlertDialog.Builder(act)
                    .setMessage(act.getString(permanent ? R.string.fork_files_confirm : R.string.fork_files_confirm_trash, chosen.size()))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.fork_files_delete, (d2, w2) -> {
                        int ok = 0;
                        File trash = new File(gameDir, "trash");
                        for(File f : chosen) {
                            if(!inside(folder, f)) continue;
                            boolean done;
                            if(permanent) {
                                done = deleteTree(f);
                            } else {
                                trash.mkdirs();
                                File dest = new File(trash, FOLDERS[index] + "__" + f.getName());
                                if(dest.exists()) dest = new File(trash, FOLDERS[index] + "__" + f.getName() + "~" + System.currentTimeMillis());
                                done = f.renameTo(dest);
                            }
                            if(done) { ok++; files.remove(f); }
                        }
                        list.clearChoices();
                        refresh.run();
                        Toast.makeText(act, act.getString(permanent ? R.string.fork_files_deleted : R.string.fork_files_moved, ok), Toast.LENGTH_SHORT).show();
                    }).show();
        });

        LinearLayout row1 = ForkUi.row(act);
        LinearLayout row2 = ForkUi.row(act);

        boolean[] bySize = {false};
        Button[] sort = new Button[1];
        sort[0] = action(act, row1, R.string.fork_files_sort_size, v -> {
            bySize[0] = !bySize[0];
            sort[0].setText(bySize[0] ? R.string.fork_files_sort_name : R.string.fork_files_sort_size);
            if(bySize[0]) java.util.Collections.sort(files, (a, c) -> Long.compare(sizeOf(c), sizeOf(a)));
            else sortByName(files);
            list.clearChoices();
            refresh.run();
        });

        if(index == MODS) {
            // A mod ending in .jar.disabled is ignored by the loader
            action(act, row1, R.string.fork_files_toggle, v -> {
                for(int i = 0; i < files.size(); i++) {
                    if(!list.isItemChecked(i)) continue;
                    File f = files.get(i);
                    if(f.isDirectory() || !inside(folder, f)) continue;
                    String n = f.getName();
                    File target;
                    if(n.endsWith(".jar")) target = new File(folder, n + ".disabled");
                    else if(n.endsWith(".jar.disabled")) target = new File(folder, n.substring(0, n.length() - ".disabled".length()));
                    else continue;
                    if(f.renameTo(target)) files.set(i, target);
                }
                list.clearChoices();
                refresh.run();
            });
            Runnable reload = () -> { dialog.dismiss(); showFolder(act, gameDir, versionId, MODS); };
            action(act, row2, R.string.fork_mr_button, v -> Modrinth.search(act, gameDir, versionId, "mod", "mods", reload));
            action(act, row2, R.string.fork_mr_update, v -> Modrinth.updateAll(act, gameDir, versionId, reload));
        }

        if(index == 2 || index == 3) {
            String type = index == 2 ? "resourcepack" : "shader";
            String dirName = FOLDERS[index];
            Runnable reload = () -> { dialog.dismiss(); showFolder(act, gameDir, versionId, index); };
            action(act, row1, R.string.fork_mr_button, v -> Modrinth.search(act, gameDir, versionId, type, dirName, reload));
        }

        if(index == 5) {
            action(act, row1, R.string.fork_gallery_button, v -> showGallery(act, folder));
        }

        if(index == SAVES) {
            action(act, row1, R.string.fork_files_backup, v -> {
                List<File> chosen = chosen(list, files);
                if(chosen.isEmpty()) return;
                new Thread(() -> {
                    int ok = 0;
                    File out = new File(gameDir, "backups");
                    out.mkdirs();
                    String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
                    for(File world : chosen) {
                        if(!inside(folder, world)) continue;
                        File zip = new File(out, world.getName() + "-" + stamp + ".zip");
                        try(ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))) {
                            InstanceTools.zipTree(z, world, world.getName());
                            ok++;
                        } catch (IOException e) {
                            zip.delete();
                        }
                    }
                    final int count = ok;
                    act.runOnUiThread(() -> Toast.makeText(act, act.getString(R.string.fork_files_backup_done, count), Toast.LENGTH_LONG).show());
                }).start();
            });
        }

        if(index == TRASH) {
            action(act, row1, R.string.fork_files_restore, v -> {
                int ok = 0;
                for(File f : chosen(list, files)) {
                    String name = f.getName();
                    int p = name.indexOf("__");
                    if(p <= 0 || !inside(folder, f)) continue;
                    String cat = name.substring(0, p);
                    boolean known = false;
                    for(int i = 0; i < PERMANENT_FROM; i++) if(FOLDERS[i].equals(cat)) known = true;
                    if(!known) continue;
                    String orig = name.substring(p + 2).replaceAll("~\\d+$", "");
                    File destDir = new File(gameDir, cat);
                    File dest = new File(destDir, orig);
                    if(dest.exists() || !inside(destDir, dest)) continue;
                    destDir.mkdirs();
                    if(f.renameTo(dest)) { ok++; files.remove(f); }
                }
                list.clearChoices();
                refresh.run();
                Toast.makeText(act, act.getString(R.string.fork_files_restored, ok), Toast.LENGTH_SHORT).show();
            });
        }

        root.addView(row1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if(row2.getChildCount() > 0) root.addView(row2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static android.graphics.Bitmap decode(File file, int target) {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while(bounds.outWidth / (sample * 2) >= target) sample *= 2;
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inSampleSize = sample;
        return android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    /** Thumbnails of the screenshots; tap one to see it big, use it as launcher banner or delete it. */
    private static void showGallery(Activity act, File folder) {
        File[] raw = folder.listFiles(f -> f.isFile() && (f.getName().endsWith(".png") || f.getName().endsWith(".jpg") || f.getName().endsWith(".jpeg")));
        List<File> shots = new ArrayList<>();
        if(raw != null) for(File f : raw) if(inside(folder, f)) shots.add(f);
        java.util.Collections.sort(shots, (a, c) -> Long.compare(c.lastModified(), a.lastModified()));

        android.widget.GridLayout grid = new android.widget.GridLayout(act);
        grid.setColumnCount(3);
        int pad = ForkUi.dp(act, 6);
        grid.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(act);
        scroll.addView(grid);
        AlertDialog dialog = new AlertDialog.Builder(act)
                .setTitle(R.string.fork_files_screenshots)
                .setView(scroll)
                .setNegativeButton(R.string.fork_files_back, null)
                .show();
        if(shots.isEmpty()) {
            Toast.makeText(act, R.string.fork_files_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        int size = ForkUi.dp(act, 96);
        new Thread(() -> {
            for(File shot : shots) {
                android.graphics.Bitmap thumb = decode(shot, 200);
                if(thumb == null) continue;
                act.runOnUiThread(() -> {
                    android.widget.ImageView image = new android.widget.ImageView(act);
                    image.setImageBitmap(thumb);
                    image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    image.setOnClickListener(v -> showShot(act, dialog, folder, shot));
                    android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
                    lp.width = size;
                    lp.height = size * 9 / 16 + ForkUi.dp(act, 20);
                    lp.setMargins(pad / 2, pad / 2, pad / 2, pad / 2);
                    grid.addView(image, lp);
                });
            }
        }).start();
    }

    private static void showShot(Activity act, AlertDialog gallery, File folder, File shot) {
        android.widget.ImageView big = new android.widget.ImageView(act);
        big.setAdjustViewBounds(true);
        big.setImageBitmap(decode(shot, 1280));
        new AlertDialog.Builder(act)
                .setTitle(shot.getName())
                .setView(big)
                .setNegativeButton(R.string.fork_files_back, null)
                .setNeutralButton(R.string.fork_gallery_banner, (d, w) -> {
                    try {
                        com.divinedoll.launcher.utils.BannerManager.saveCustom(act, android.net.Uri.fromFile(shot));
                        Toast.makeText(act, R.string.fork_gallery_banner_done, Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        Toast.makeText(act, act.getString(R.string.fork_op_error, String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
                    }
                })
                .setPositiveButton(R.string.fork_files_delete, (d, w) -> {
                    if(inside(folder, shot) && shot.delete()) {
                        gallery.dismiss();
                        showGallery(act, folder);
                    }
                })
                .show();
    }

    private static List<File> chosen(ListView list, List<File> files) {
        List<File> out = new ArrayList<>();
        for(int i = 0; i < files.size(); i++) if(list.isItemChecked(i)) out.add(files.get(i));
        return out;
    }
}

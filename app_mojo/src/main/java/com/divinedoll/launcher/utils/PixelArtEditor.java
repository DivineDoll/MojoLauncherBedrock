package com.divinedoll.launcher.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.divinedoll.launcher.R;

/**
 * Small pixel art editor. Drawings are saved as PNG in the app's files and can be exported
 * enlarged to Downloads, ready to be used as an image for a control button.
 */
public class PixelArtEditor {
    private static final int[] PALETTE = {
            0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFF39393C,
            0xFFE04646, 0xFFE8802E, 0xFFE8B02E, 0xFF7CD65A,
            0xFF3C8527, 0xFF29B6C5, 0xFF2E8BE6, 0xFF9B59E8,
            0xFFE86AB5, 0xFF8B5A2B, 0xFF5B3A1A, 0xFFF2D6A8};

    /** The saved drawings, newest first. */
    public static File[] saved(Context c) {
        File[] files = dir(c).listFiles((d, n) -> n.endsWith(".png"));
        if(files == null) return new File[0];
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    /**
     * Scales a drawing up by a whole number without blurring and centers it in a transparent
     * image of the given size, so it keeps its square pixels on any button shape.
     */
    public static Bitmap fitTo(Bitmap art, int width, int height) {
        int scale = Math.max(1, Math.min(width / art.getWidth(), height / art.getHeight()));
        Bitmap big = Bitmap.createScaledBitmap(art, art.getWidth() * scale, art.getHeight() * scale, false);
        Bitmap out = Bitmap.createBitmap(Math.max(width, big.getWidth()), Math.max(height, big.getHeight()), Bitmap.Config.ARGB_8888);
        new android.graphics.Canvas(out).drawBitmap(big, (out.getWidth() - big.getWidth()) / 2f, (out.getHeight() - big.getHeight()) / 2f, null);
        return out;
    }

    public static File directory(Context c) {
        return dir(c);
    }

    private static Bitmap fromRows(String[] rows, int[] colors, String keys) {
        Bitmap bmp = Bitmap.createBitmap(rows[0].length(), rows.length, Bitmap.Config.ARGB_8888);
        for(int y = 0; y < rows.length; y++) {
            for(int x = 0; x < rows[y].length(); x++) {
                int i = keys.indexOf(rows[y].charAt(x));
                bmp.setPixel(x, y, i < 0 ? 0 : colors[i]);
            }
        }
        return bmp;
    }

    /** Button template with the dark bottom edge removed and a darker face, like a pressed button. */
    private static Bitmap pressedButton(Context c) {
        Bitmap bmp = buttonTemplate(c);
        for(int y = 0; y < bmp.getHeight(); y++) {
            for(int x = 0; x < bmp.getWidth(); x++) {
                int p = bmp.getPixel(x, y);
                if(p == 0xFF39393C) bmp.setPixel(x, y, 0xFF686868);
                else if(p == 0xFF808080) bmp.setPixel(x, y, 0xFF6F6F6F);
            }
        }
        return bmp;
    }

    private static Bitmap inventorySlot() {
        int n = 18;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        for(int y = 0; y < n; y++) {
            for(int x = 0; x < n; x++) {
                int color = 0xFF8B8B8B;
                if(x == 0 || y == 0) color = 0xFF373737;
                else if(x == n - 1 || y == n - 1) color = 0xFFFFFFFF;
                if((x == n - 1 && y == 0) || (x == 0 && y == n - 1)) color = 0xFF8B8B8B;
                bmp.setPixel(x, y, color);
            }
        }
        return bmp;
    }

    private static Bitmap heart() {
        String[] rows = {
                "..##.##..",
                ".#RR#RR#.",
                "#RWRRRRR#",
                "#RRRRRRR#",
                "#RRRRRRR#",
                ".#RRRRR#.",
                "..#RRR#..",
                "...#R#...",
                "....#...."};
        return fromRows(rows, new int[]{0xFF000000, 0xFFE02020, 0xFFFFFFFF}, "#RW");
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "pixelart");
        if(!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** The gray button as a 32x33 grid, every cell can be edited. */
    private static Bitmap buttonTemplate(Context c) {
        Bitmap bmp = BitmapFactory.decodeResource(c.getResources(), R.drawable.pixel_template_button);
        return bmp.copy(Bitmap.Config.ARGB_8888, true);
    }

    private static String cleanName(String raw) {
        String n = raw.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return n.isEmpty() ? "pixelart" : n;
    }

    private static final int[][] PALETTES = {
            PALETTE,
            {0xFFF9FFFE, 0xFFF9801D, 0xFFC74EBD, 0xFF3AB3DA, 0xFFFED83D, 0xFF80C71F, 0xFFF38BAA, 0xFF474F52,
                    0xFF9D9D97, 0xFF169C9C, 0xFF8932B8, 0xFF3C44AA, 0xFF835432, 0xFF5E7C16, 0xFFB02E26, 0xFF1D1D21},
            {0xFF1A0505, 0xFF3B0D0D, 0xFF5C1414, 0xFF7D1D1D, 0xFFA12A1A, 0xFFC4472A, 0xFFE06A3A, 0xFFF29A4A,
                    0xFFFFD27A, 0xFF4A2A1A, 0xFF6B3E26, 0xFF8C5A38, 0xFF2B1E2E, 0xFF4D2E4D, 0xFF7A1F3D, 0xFFD9B8A0},
            {0xFF000000, 0xFF111111, 0xFF222222, 0xFF333333, 0xFF444444, 0xFF555555, 0xFF666666, 0xFF777777,
                    0xFF888888, 0xFF999999, 0xFFAAAAAA, 0xFFBBBBBB, 0xFFCCCCCC, 0xFFDDDDDD, 0xFFEEEEEE, 0xFFFFFFFF},
            {0xFFFFB3BA, 0xFFFFDFBA, 0xFFFFFFBA, 0xFFBAFFC9, 0xFFBAE1FF, 0xFFD5BAFF, 0xFFFFC8DD, 0xFFCDEAC0,
                    0xFFA0CED9, 0xFFEAD7D7, 0xFFF3E5AB, 0xFFC9E4DE, 0xFFE6CCB2, 0xFFB5C0D0, 0xFFF4A6A6, 0xFFFFFFFF}
    };

    private static File paletteDir(Context c) {
        File d = new File(c.getFilesDir(), "palettes");
        if(!d.isDirectory()) d.mkdirs();
        return d;
    }

    private static void savePalette(Context c, String name, int[] colors) throws Exception {
        org.json.JSONArray array = new org.json.JSONArray();
        for(int color : colors) array.put(String.format("#%06X", color & 0xFFFFFF));
        try(FileOutputStream out = new FileOutputStream(new File(paletteDir(c), name + ".json"))) {
            out.write(array.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static int[] loadPalette(File f) throws Exception {
        byte[] data = new byte[(int) f.length()];
        try(java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int read = 0;
            while(read < data.length) {
                int n = in.read(data, read, data.length - read);
                if(n < 0) break;
                read += n;
            }
        }
        org.json.JSONArray array = new org.json.JSONArray(new String(data, java.nio.charset.StandardCharsets.UTF_8));
        int[] colors = new int[PALETTE.length];
        for(int i = 0; i < colors.length; i++) colors[i] = Color.parseColor(array.getString(i % array.length())) | 0xFF000000;
        return colors;
    }

    private static androidx.activity.ComponentActivity activityOf(Context c) {
        while(c instanceof android.content.ContextWrapper) {
            if(c instanceof androidx.activity.ComponentActivity) return (androidx.activity.ComponentActivity) c;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static void liftSelection(Context c, PixelCanvasView canvas, boolean cut) {
        if(!canvas.hasSelection()) {
            Toast.makeText(c, R.string.fork_pixel_sel_none, Toast.LENGTH_SHORT).show();
        } else if(!canvas.liftSelection(cut)) {
            Toast.makeText(c, R.string.fork_pixel_sel_empty, Toast.LENGTH_SHORT).show();
        }
    }

    private static Button toggle(Context c, int label, boolean on) {
        Button b = ForkUi.button(c, c.getString(label), on);
        b.setTextSize(11);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    public static void show(Context c) {
        Dialog dialog = new Dialog(c, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        int pad = ForkUi.dp(c, 12);
        int[] palette = PALETTE.clone();
        View[] swatches = new View[PALETTE.length];

        View[] overlay = new View[1];
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(ThemeColors.background());
        root.setPadding(pad, pad, pad, pad);

        PixelCanvasView canvas = new PixelCanvasView(c);
        root.addView(canvas, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        ScrollView scroll = new ScrollView(c);
        LinearLayout side = new LinearLayout(c);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(pad, 0, 0, 0);
        scroll.addView(side);
        root.addView(scroll, new LinearLayout.LayoutParams(ForkUi.dp(c, 300), ViewGroup.LayoutParams.MATCH_PARENT));

        side.addView(ForkUi.text(c, c.getString(R.string.fork_pixel_title), 16, true));

        int[] toolIds = {PixelCanvasView.PENCIL, PixelCanvasView.ERASER, PixelCanvasView.FILL, PixelCanvasView.PICK,
                PixelCanvasView.LINE, PixelCanvasView.RECT, PixelCanvasView.CIRCLE, PixelCanvasView.SELECT};
        int[] toolNames = {R.string.fork_pixel_pencil, R.string.fork_pixel_eraser, R.string.fork_pixel_fill, R.string.fork_pixel_pick,
                R.string.fork_pixel_line, R.string.fork_pixel_rect, R.string.fork_pixel_circle, R.string.fork_pixel_select};
        Button[] toolButtons = new Button[toolIds.length];
        LinearLayout toolRow1 = ForkUi.row(c), toolRow2 = ForkUi.row(c);
        for(int i = 0; i < toolIds.length; i++) {
            final int id = toolIds[i];
            Button b = toggle(c, toolNames[i], i == 0);
            toolButtons[i] = b;
            b.setOnClickListener(v -> {
                canvas.setTool(id);
                for(int k = 0; k < toolIds.length; k++) McStyle.button(toolButtons[k], toolIds[k] == id);
            });
            (i < 4 ? toolRow1 : toolRow2).addView(b, ForkUi.weight(1f));
        }
        side.addView(toolRow1);
        side.addView(toolRow2);

        LinearLayout selectRow = ForkUi.row(c);
        Button selCopy = ForkUi.button(c, c.getString(R.string.fork_pixel_sel_copy), false);
        Button selMove = ForkUi.button(c, c.getString(R.string.fork_pixel_sel_move), false);
        selCopy.setOnClickListener(v -> liftSelection(c, canvas, false));
        selMove.setOnClickListener(v -> liftSelection(c, canvas, true));
        selectRow.addView(selCopy, ForkUi.weight(1f));
        selectRow.addView(selMove, ForkUi.weight(1f));
        side.addView(selectRow);

        LinearLayout options = ForkUi.row(c);
        Button mirror = toggle(c, R.string.fork_pixel_mirror, false);
        Button shapeFill = toggle(c, R.string.fork_pixel_shape_fill, false);
        mirror.setOnClickListener(v -> {
            canvas.setMirror(!canvas.isMirror());
            McStyle.button(mirror, canvas.isMirror());
        });
        shapeFill.setOnClickListener(v -> {
            canvas.setShapeFill(!canvas.isShapeFill());
            McStyle.button(shapeFill, canvas.isShapeFill());
        });
        options.addView(mirror, ForkUi.weight(1f));
        options.addView(shapeFill, ForkUi.weight(1f));
        side.addView(options);

        LinearLayout layerRow = ForkUi.row(c), showRow = ForkUi.row(c);
        Button[] layerButtons = {toggle(c, R.string.fork_pixel_layer1, true), toggle(c, R.string.fork_pixel_layer2, false)};
        Button[] showButtons = {toggle(c, R.string.fork_pixel_show1, true), toggle(c, R.string.fork_pixel_show2, true)};
        for(int i = 0; i < 2; i++) {
            final int layer = i;
            layerButtons[i].setOnClickListener(v -> {
                canvas.setActiveLayer(layer);
                McStyle.button(layerButtons[0], layer == 0);
                McStyle.button(layerButtons[1], layer == 1);
            });
            showButtons[i].setOnClickListener(v -> {
                canvas.setLayerVisible(layer, !canvas.isLayerVisible(layer));
                McStyle.button(showButtons[layer], canvas.isLayerVisible(layer));
            });
            layerRow.addView(layerButtons[i], ForkUi.weight(1f));
            showRow.addView(showButtons[i], ForkUi.weight(1f));
        }
        side.addView(layerRow);
        side.addView(showRow);

        LinearLayout actions = ForkUi.row(c);
        Button undo = toggle(c, R.string.fork_pixel_undo, false);
        Button clear = toggle(c, R.string.fork_pixel_clear, false);
        undo.setOnClickListener(v -> canvas.undo());
        clear.setOnClickListener(v -> canvas.clear());
        actions.addView(undo, ForkUi.weight(1f));
        actions.addView(clear, ForkUi.weight(1f));
        side.addView(actions);

        LinearLayout zoomRow = ForkUi.row(c);
        Button zoomIn = ForkUi.button(c, "+", false);
        Button zoomOut = ForkUi.button(c, "-", false);
        Button zoomFit = ForkUi.button(c, "1:1", false);
        zoomIn.setOnClickListener(v -> canvas.zoomBy(1.5f));
        zoomOut.setOnClickListener(v -> canvas.zoomBy(1f / 1.5f));
        zoomFit.setOnClickListener(v -> canvas.resetZoom());
        zoomRow.addView(zoomIn, ForkUi.weight(1f));
        zoomRow.addView(zoomOut, ForkUi.weight(1f));
        zoomRow.addView(zoomFit, ForkUi.weight(1f));
        side.addView(zoomRow);

        LinearLayout stampRow = new LinearLayout(c);
        stampRow.setOrientation(LinearLayout.HORIZONTAL);
        stampRow.setVisibility(View.GONE);
        Button stampSmaller = ForkUi.button(c, "-", false);
        Button stampBigger = ForkUi.button(c, "+", false);
        Button stampApply = ForkUi.button(c, c.getString(R.string.fork_pixel_stamp_apply), true);
        Button stampCancel = ForkUi.button(c, c.getString(android.R.string.cancel), false);
        stampSmaller.setOnClickListener(v -> canvas.resizeStamp(-Math.max(1, canvas.getStampWidth() / 10)));
        stampBigger.setOnClickListener(v -> canvas.resizeStamp(Math.max(1, canvas.getStampWidth() / 10)));
        stampApply.setOnClickListener(v -> canvas.commitStamp());
        stampCancel.setOnClickListener(v -> canvas.cancelStamp());
        stampRow.addView(stampSmaller, ForkUi.weight(1f));
        stampRow.addView(stampBigger, ForkUi.weight(1f));
        stampRow.addView(stampApply, ForkUi.weight(2f));
        stampRow.addView(stampCancel, ForkUi.weight(2f));
        canvas.setStampListener(() -> stampRow.setVisibility(canvas.isStamping() ? View.VISIBLE : View.GONE));
        side.addView(stampRow);

        LinearLayout floating = new LinearLayout(c);
        floating.setOrientation(LinearLayout.HORIZONTAL);
        floating.setVisibility(View.GONE);
        floating.setAlpha(0.8f);
        Button floatUndo = ForkUi.button(c, c.getString(R.string.fork_pixel_undo), false);
        floatUndo.setOnClickListener(v -> canvas.undo());
        Button floatExit = ForkUi.button(c, c.getString(R.string.fork_pixel_preview_exit), true);
        Button floatPencil = ForkUi.button(c, c.getString(R.string.fork_pixel_pencil), false);
        floatPencil.setOnClickListener(v -> canvas.setTool(PixelCanvasView.PENCIL));
        Button floatEraser = ForkUi.button(c, c.getString(R.string.fork_pixel_eraser), false);
        floatEraser.setOnClickListener(v -> canvas.setTool(PixelCanvasView.ERASER));
        Button floatColor = ForkUi.button(c, c.getString(R.string.fork_pixel_color_quick), false);
        floatColor.setOnClickListener(v -> {
            android.widget.GridLayout grid = new android.widget.GridLayout(c);
            grid.setColumnCount(4);
            AlertDialog colorDialog = new AlertDialog.Builder(c).setView(grid).create();
            for(int i = 0; i < palette.length; i++) {
                final int index = i;
                View swatch = new View(c);
                swatch.setBackground(ForkUi.box(c, palette[i], 0xFFFFFFFF, 1));
                swatch.setOnClickListener(x -> {
                    canvas.setColor(palette[index]);
                    colorDialog.dismiss();
                });
                android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
                lp.width = ForkUi.dp(c, 52);
                lp.height = ForkUi.dp(c, 52);
                lp.setMargins(ForkUi.dp(c, 3), ForkUi.dp(c, 3), ForkUi.dp(c, 3), ForkUi.dp(c, 3));
                grid.addView(swatch, lp);
            }
            colorDialog.show();
        });
        floating.addView(floatPencil);
        floating.addView(floatEraser);
        floating.addView(floatColor);
        floating.addView(floatUndo);
        floating.addView(floatExit);
        overlay[0] = floating;

        Button previewButton = ForkUi.button(c, c.getString(R.string.fork_pixel_preview), false);
        Runnable leavePreview = () -> {
            canvas.setPreview(false, null);
            floating.setVisibility(View.GONE);
            scroll.setVisibility(View.VISIBLE);
            root.setPadding(pad, pad, pad, pad);
        };
        floatExit.setOnClickListener(v -> leavePreview.run());
        previewButton.setOnClickListener(v -> {
            scroll.setVisibility(View.GONE);
            root.setPadding(0, 0, 0, 0);
            floating.setVisibility(View.VISIBLE);
            canvas.setPreview(true, null);
            Toast.makeText(c, R.string.fork_pixel_preview_hint, Toast.LENGTH_SHORT).show();
        });
        side.addView(previewButton);

        LinearLayout paletteBox = new LinearLayout(c);
        paletteBox.setOrientation(LinearLayout.VERTICAL);
        paletteBox.setPadding(0, pad, 0, pad);
        LinearLayout rowView = null;
        for(int i = 0; i < PALETTE.length; i++) {
            if(i % 4 == 0) {
                rowView = ForkUi.row(c);
                paletteBox.addView(rowView);
            }
            final int index = i;
            View sw = new View(c);
            swatches[i] = sw;
            sw.setBackground(ForkUi.box(c, palette[i], 0xFFFFFFFF, 1));
            sw.setOnClickListener(v -> canvas.setColor(palette[index]));
            sw.setOnLongClickListener(v -> {
                palette[index] = canvas.getColor();
                sw.setBackground(ForkUi.box(c, palette[index], 0xFFFFFFFF, 1));
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ForkUi.dp(c, 34), 1f);
            lp.setMargins(ForkUi.dp(c, 2), ForkUi.dp(c, 2), ForkUi.dp(c, 2), ForkUi.dp(c, 2));
            rowView.addView(sw, lp);
        }
        side.addView(paletteBox);
        TextView paletteHint = ForkUi.text(c, c.getString(R.string.fork_pixel_palette_hint), 11, false);
        paletteHint.setTextColor(ForkUi.TAB_IDLE);
        side.addView(paletteHint);

        Runnable refreshSwatches = () -> {
            for(int i = 0; i < swatches.length; i++) swatches[i].setBackground(ForkUi.box(c, palette[i], 0xFFFFFFFF, 1));
        };
        LinearLayout paletteRow = ForkUi.row(c);
        Button palettes = toggle(c, R.string.fork_pixel_palettes, false);
        Button custom = toggle(c, R.string.fork_pixel_custom_color, false);
        palettes.setOnClickListener(v -> {
            File[] saved = paletteDir(c).listFiles((d, n) -> n.endsWith(".json"));
            if(saved == null) saved = new File[0];
            java.util.Arrays.sort(saved);
            final File[] savedFinal = saved;
            String[] names = new String[PALETTES.length + savedFinal.length];
            int[] presetNames = {R.string.fork_pixel_pal_default, R.string.fork_pixel_pal_minecraft, R.string.fork_pixel_pal_nether,
                    R.string.fork_pixel_pal_grays, R.string.fork_pixel_pal_pastel};
            for(int i = 0; i < PALETTES.length; i++) names[i] = c.getString(presetNames[i]);
            for(int i = 0; i < savedFinal.length; i++) names[PALETTES.length + i] = savedFinal[i].getName().replace(".json", "");
            new AlertDialog.Builder(c).setTitle(R.string.fork_pixel_palettes).setItems(names, (d, which) -> {
                try {
                    int[] chosen = which < PALETTES.length ? PALETTES[which] : loadPalette(savedFinal[which - PALETTES.length]);
                    System.arraycopy(chosen, 0, palette, 0, palette.length);
                    refreshSwatches.run();
                } catch(Exception e) {
                    Toast.makeText(c, R.string.fork_pixel_error, Toast.LENGTH_LONG).show();
                }
            }).setPositiveButton(R.string.fork_pixel_palette_save, (d, w) -> {
                EditText input = new EditText(c);
                input.setHint(R.string.fork_pixel_name_hint);
                input.setSingleLine();
                new AlertDialog.Builder(c).setTitle(R.string.fork_pixel_palette_save).setView(input)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (d2, w2) -> {
                            try {
                                savePalette(c, cleanName(input.getText().toString()), palette);
                            } catch(Exception e) {
                                Toast.makeText(c, R.string.fork_pixel_error, Toast.LENGTH_LONG).show();
                            }
                        }).show();
            }).setNegativeButton(android.R.string.cancel, null).show();
        });
        custom.setOnClickListener(v -> ColorPicker.show(c, R.string.fork_pixel_custom_color, canvas.getColor(),
                Color.WHITE, canvas::setColor));
        paletteRow.addView(palettes, ForkUi.weight(1f));
        paletteRow.addView(custom, ForkUi.weight(1f));
        side.addView(paletteRow);
        canvas.setListener(color -> { });

        Button template = ForkUi.button(c, c.getString(R.string.fork_pixel_template), false);
        template.setOnClickListener(v -> {
            String[] names = {c.getString(R.string.fork_pixel_tpl_button), c.getString(R.string.fork_pixel_tpl_blank16),
                    c.getString(R.string.fork_pixel_tpl_blank32), c.getString(R.string.fork_pixel_tpl_blank64),
                    c.getString(R.string.fork_pixel_tpl_pressed), c.getString(R.string.fork_pixel_tpl_slot),
                    c.getString(R.string.fork_pixel_tpl_heart)};
            new AlertDialog.Builder(c).setTitle(R.string.fork_pixel_template).setItems(names, (d, which) -> {
                if(which == 0) canvas.load(buttonTemplate(c));
                else if(which == 4) canvas.load(pressedButton(c));
                else if(which == 5) canvas.load(inventorySlot());
                else if(which == 6) canvas.load(heart());
                else {
                    int n = which == 1 ? 16 : which == 2 ? 32 : 64;
                    canvas.setGridSize(n, n);
                }
            }).show();
        });
        side.addView(template);
        canvas.load(buttonTemplate(c));

        Button importImage = ForkUi.button(c, c.getString(R.string.fork_pixel_import), false);
        importImage.setOnClickListener(v -> {
            androidx.activity.ComponentActivity activity = activityOf(c);
            if(activity == null) {
                Toast.makeText(c, R.string.fork_pixel_error, Toast.LENGTH_LONG).show();
                return;
            }
            @SuppressWarnings("rawtypes")
            androidx.activity.result.ActivityResultLauncher[] launcher = new androidx.activity.result.ActivityResultLauncher[1];
            launcher[0] = activity.getActivityResultRegistry().register("pixel_import_" + System.nanoTime(),
                    new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                        launcher[0].unregister();
                        if(uri != null) askImportOptions(c, canvas, uri, palette);
                    });
            launcher[0].launch("image/*");
        });
        side.addView(importImage);

        Button letters = ForkUi.button(c, c.getString(R.string.fork_pixel_letters), false);
        letters.setOnClickListener(v -> {
            android.widget.GridLayout grid = new android.widget.GridLayout(c);
            grid.setColumnCount(6);
            ScrollView letterScroll = new ScrollView(c);
            LinearLayout letterBox = new LinearLayout(c);
            letterBox.setOrientation(LinearLayout.VERTICAL);
            letterBox.addView(grid);
            letterScroll.addView(letterBox);
            AlertDialog letterDialog = new AlertDialog.Builder(c).setTitle(R.string.fork_pixel_letters).setView(letterScroll).show();
            Button writeText = ForkUi.button(c, c.getString(R.string.fork_pixel_text), true);
            writeText.setOnClickListener(x -> {
                letterDialog.dismiss();
                EditText input = new EditText(c);
                input.setHint(R.string.fork_pixel_text_hint);
                input.setSingleLine(true);
                new AlertDialog.Builder(c).setTitle(R.string.fork_pixel_text).setView(input)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            if(input.getText().length() == 0) return;
                            canvas.startStamp(PixelFont.text(input.getText().toString(), canvas.getColor()));
                            Toast.makeText(c, R.string.fork_pixel_stamp_hint, Toast.LENGTH_LONG).show();
                        })
                        .setNegativeButton(android.R.string.cancel, null).show();
            });
            letterBox.addView(writeText, 0);
            for(int i = 0; i < PixelFont.CHARS.length(); i++) {
                char ch = PixelFont.CHARS.charAt(i);
                if(ch == ' ') continue;
                Button b = ForkUi.button(c, String.valueOf(ch), false);
                b.setMinWidth(0);
                b.setMinimumWidth(0);
                b.setOnClickListener(x -> {
                    letterDialog.dismiss();
                    canvas.startStamp(PixelFont.glyph(ch, canvas.getColor()));
                    Toast.makeText(c, R.string.fork_pixel_stamp_hint, Toast.LENGTH_LONG).show();
                });
                grid.addView(b, new android.widget.GridLayout.LayoutParams());
            }
        });
        side.addView(letters);

        EditText name = new EditText(c);
        name.setHint(R.string.fork_pixel_name_hint);
        name.setHintTextColor(0xFF888888);
        name.setTextColor(Color.WHITE);
        name.setInputType(InputType.TYPE_CLASS_TEXT);
        name.setSingleLine();
        McStyle.text(name);
        side.addView(name);

        Button save = ForkUi.button(c, c.getString(R.string.fork_pixel_save), true);
        save.setOnClickListener(v -> {
            try {
                File f = new File(dir(c), cleanName(name.getText().toString()) + ".png");
                writePng(canvas.toBitmap(), f);
                Toast.makeText(c, c.getString(R.string.fork_pixel_saved, f.getName()), Toast.LENGTH_SHORT).show();
            } catch(IOException e) {
                Toast.makeText(c, R.string.fork_pixel_error, Toast.LENGTH_LONG).show();
            }
        });
        side.addView(save);

        Button export = ForkUi.button(c, c.getString(R.string.fork_pixel_export), false);
        export.setOnClickListener(v -> {
            try {
                Bitmap src = canvas.toBitmap();
                int scale = Math.max(1, 256 / Math.max(src.getWidth(), src.getHeight()));
                Bitmap big = Bitmap.createScaledBitmap(src, src.getWidth() * scale, src.getHeight() * scale, false);
                File tmp = new File(c.getCacheDir(), cleanName(name.getText().toString()) + "_x" + scale + ".png");
                writePng(big, tmp);
                String file = OnlineSkins.exportToDownloads(c, tmp);
                tmp.delete();
                Toast.makeText(c, c.getString(R.string.fork_pixel_exported, file), Toast.LENGTH_LONG).show();
            } catch(IOException e) {
                Toast.makeText(c, R.string.fork_pixel_error, Toast.LENGTH_LONG).show();
            }
        });
        side.addView(export);

        Button open = ForkUi.button(c, c.getString(R.string.fork_pixel_open), false);
        open.setOnClickListener(v -> {
            File[] files = dir(c).listFiles((d, n) -> n.endsWith(".png"));
            if(files == null || files.length == 0) {
                Toast.makeText(c, R.string.fork_pixel_none, Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[files.length];
            for(int i = 0; i < files.length; i++) names[i] = files[i].getName();
            new AlertDialog.Builder(c).setTitle(R.string.fork_pixel_open).setItems(names, (d, which) -> {
                Bitmap bmp = BitmapFactory.decodeFile(files[which].getAbsolutePath());
                if(bmp == null || bmp.getWidth() > 128 || bmp.getHeight() > 128) {
                    Toast.makeText(c, R.string.fork_pixel_error, Toast.LENGTH_LONG).show();
                    return;
                }
                canvas.load(bmp.copy(Bitmap.Config.ARGB_8888, false));
                name.setText(names[which].replace(".png", ""));
            }).show();
        });
        side.addView(open);

        Button close = ForkUi.button(c, c.getString(android.R.string.ok), false);
        close.setOnClickListener(v -> dialog.dismiss());
        side.addView(close);

        android.widget.FrameLayout frame = new android.widget.FrameLayout(c);
        frame.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.FrameLayout.LayoutParams fp = new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        fp.setMargins(pad, pad, pad, pad);
        frame.addView(overlay[0], fp);
        dialog.setContentView(frame);
        dialog.show();
    }

    /** Asks for the size and the colors, then converts the picked image into the canvas. */
    private static void askImportOptions(Context c, PixelCanvasView canvas, android.net.Uri uri, int[] palette) {
        String[] sizes = {"16", "32", "64"};
        new AlertDialog.Builder(c).setTitle(R.string.fork_pixel_import_size).setItems(sizes, (d, which) -> {
            int side = Integer.parseInt(sizes[which]);
            String[] modes = {c.getString(R.string.fork_pixel_import_palette), c.getString(R.string.fork_pixel_import_original)};
            new AlertDialog.Builder(c).setTitle(R.string.fork_pixel_import).setItems(modes, (d2, mode) -> {
                String[] places = {c.getString(R.string.fork_pixel_import_over), c.getString(R.string.fork_pixel_import_replace)};
                new AlertDialog.Builder(c).setTitle(R.string.fork_pixel_import_place).setItems(places, (d3, place) ->
                        com.divinedoll.launcher.MojoApplication.sExecutorService.execute(() -> {
                            try {
                                Bitmap result = PixelImport.convert(c, uri, side, mode == 0 ? palette.clone() : null);
                                com.divinedoll.launcher.Tools.runOnUiThread(() -> {
                                    if(place == 0) {
                                        canvas.startStamp(result);
                                        Toast.makeText(c, R.string.fork_pixel_stamp_hint, Toast.LENGTH_LONG).show();
                                    } else canvas.load(result);
                                });
                            } catch(Exception e) {
                                com.divinedoll.launcher.Tools.runOnUiThread(() ->
                                        Toast.makeText(c, R.string.fork_pixel_import_error, Toast.LENGTH_LONG).show());
                            }
                        })).show();
            }).show();
        }).show();
    }

    private static void writePng(Bitmap bmp, File f) throws IOException {
        try(FileOutputStream out = new FileOutputStream(f)) {
            if(!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("compress");
        }
    }
}

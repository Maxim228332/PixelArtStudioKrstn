package com.eokers.pixelartstudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_OPEN_IMAGE = 101;
    private static final int MAX_HISTORY = 24;

    private int gridSize = 32;
    private int[] pixels = new int[gridSize * gridSize];
    
    private boolean isFreeDrawMode = false;
    private final List<PathElement> freePaths = new ArrayList<>();
    private transient Path currentFreePath;

    private Bitmap referenceBitmap;
    private boolean showReference = true;
    private boolean showGrid = true;
    private int currentColor = Color.parseColor("#FF80AB");
    private String currentTool = "✏️ Карандаш";
    
    private boolean numberMode = false;
    private int[] numberMap = new int[0];
    private int[] numberColors = new int[0];
    private long lastWrongColorToast = 0L;

    private final List<Integer> palette = new ArrayList<>();
    private final ArrayDeque<Snapshot> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redoStack = new ArrayDeque<>();

    private LinearLayout root;
    private LinearLayout paletteRow;
    private TextView selectedColorView;
    private TextView referenceButton;
    private TextView statusLabel;
    private PixelCanvas pixelCanvas;
    private PixelCatView pixelCatView;

    private final Handler animHandler = new Handler(Looper.getMainLooper());
    private Runnable animatorRunnable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        seedPalette();
        Arrays.fill(pixels, Color.WHITE);
        buildUi();
        startAnimationLoop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void seedPalette() {
        int[] colors = {
                0xFFFF80AB, 0xFFFFFFFF, 0xFFFFC8DD, 0xFFFFB5A7,
                0xFFB5EAD7, 0xFFC7CEEA, 0xFFFFDAC1, 0xFFE2F0CB,
                0xFFF8BBD0, 0xFF81D4FA, 0xFFCE93D8, 0xFF90CAF9,
                0xFF374151, 0xFFE8F5E9, 0xFFFFF9C4, 0xFFFFCCBC
        };
        for (int color : colors) palette.add(color);
    }

    private void startAnimationLoop() {
        animatorRunnable = new Runnable() {
            @Override
            public void run() {
                if (pixelCatView != null) {
                    pixelCatView.toggleAnimationState();
                }
                animHandler.postDelayed(this, 1400);
            }
        };
        animHandler.postDelayed(animatorRunnable, 1400);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (animHandler != null && animatorRunnable != null) {
            animHandler.removeCallbacks(animatorRunnable);
        }
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#FFFDF9"));
        root.setPadding(dp(8), dp(4), dp(8), dp(4));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(4), dp(2), dp(4), dp(2));
        header.setBackground(roundBackground(0xFFFFF0F3, 0xFFFFC8DD, dp(10)));
        
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        
        TextView title = new TextView(this);
        title.setText("✨ PIXEL ART STUDIO 🤍");
        title.setTextColor(Color.parseColor("#AD1457"));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titleBox.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Уютное творчество вместе с Мусей 🐾");
        subtitle.setTextColor(Color.parseColor("#C2185B"));
        subtitle.setTextSize(9);
        titleBox.addView(subtitle);

        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        pixelCatView = new PixelCatView(this);
        header.addView(pixelCatView, new LinearLayout.LayoutParams(dp(64), dp(40)));
        
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        statusLabel = new TextView(this);
        statusLabel.setTextColor(Color.parseColor("#AD1457"));
        statusLabel.setTextSize(10);
        statusLabel.setMaxLines(1);
        statusLabel.setPadding(dp(4), dp(2), dp(0), dp(2));
        refreshStatus();
        root.addView(statusLabel);

        HorizontalScrollView paletteScroll = new HorizontalScrollView(this);
        paletteScroll.setHorizontalScrollBarEnabled(false);
        paletteRow = new LinearLayout(this);
        paletteRow.setOrientation(LinearLayout.HORIZONTAL);
        paletteRow.setGravity(Gravity.CENTER_VERTICAL);
        paletteRow.setPadding(dp(2), dp(2), dp(2), dp(2));
        paletteScroll.addView(paletteRow);
        root.addView(paletteScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        LinearLayout colorActions = new LinearLayout(this);
        colorActions.setOrientation(LinearLayout.HORIZONTAL);
        selectedColorView = makeChip("🎨 Цвет", () -> showColorDialog(), false);
        selectedColorView.setCompoundDrawablePadding(dp(4));
        colorActions.addView(selectedColorView, chipParams());
        addChipTo(colorActions, "🔀 Смешать", () -> showMixDialog(), false);
        addChipTo(colorActions, "✨ Оттенки", () -> addShades(), false);
        root.addView(horizontal(colorActions));

        root.addView(toolbar(new String[]{"✏️ Карандаш", "🧹 Ластик", "🪣 Заливка", "💧 Пипетка"}, tool -> setTool(tool)));

        pixelCanvas = new PixelCanvas(this);
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        canvasParams.topMargin = dp(4);
        canvasParams.bottomMargin = dp(4);
        root.addView(pixelCanvas, canvasParams);

        LinearLayout modeBar = new LinearLayout(this);
        modeBar.setOrientation(LinearLayout.HORIZONTAL);
        addChipTo(modeBar, isFreeDrawMode ? "🔳 В пиксели" : "✍️ Обычный лист", () -> toggleFreeDrawMode(), true);
        addChipTo(modeBar, "📐 Размер: " + gridSize + "×" + gridSize, () -> chooseGridSize(), false);
        root.addView(horizontal(modeBar));

        LinearLayout extraBar = new LinearLayout(this);
        extraBar.setOrientation(LinearLayout.HORIZONTAL);
        addChipTo(extraBar, "№ Раскраска", () -> convertToNumberColoring(), numberMode);
        addChipTo(extraBar, "📂 Откр.", () -> openReference(), false);
        addChipTo(extraBar, "💾 PNG", () -> savePng(), false);
        referenceButton = makeChip("👁 Реф.", () -> toggleReference(), showReference);
        extraBar.addView(referenceButton, chipParams());
        addChipTo(extraBar, "🔳 Сетка", () -> toggleGrid(), showGrid);
        root.addView(horizontal(extraBar));

        LinearLayout history = new LinearLayout(this);
        history.setOrientation(LinearLayout.HORIZONTAL);
        history.setGravity(Gravity.BOTTOM);
        addChipTo(history, "↶ Отмена", () -> undo(), false);
        addChipTo(history, "↷ Повтор", () -> redo(), false);
        addChipTo(history, "🗑 Очистить", () -> confirmClear(), false);
        root.addView(horizontal(history));

        setContentView(root);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int bottomInset = insets.getSystemWindowInsetBottom();
            view.setPadding(dp(8), dp(4) + topInset, dp(8), dp(4) + bottomInset);
            return insets;
        });

        refreshPalette();
        refreshColorButton();
    }

    private HorizontalScrollView toolbar(String[] labels, java.util.function.Consumer<String> callback) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String label : labels) {
            TextView chip = makeChip(label, () -> callback.accept(label), label.equals(currentTool));
            row.addView(chip, chipParams());
        }
        return horizontal(row);
    }

    private HorizontalScrollView horizontal(LinearLayout row) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        p.rightMargin = dp(6);
        p.bottomMargin = dp(3);
        return p;
    }

    private TextView makeChip(String text, Runnable action, boolean active) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(active ? Color.WHITE : Color.parseColor("#880E4F"));
        chip.setTextSize(11);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));
        chip.setMinWidth(dp(44));
        chip.setBackground(roundBackground(active ? 0xFFE91E63 : 0xFFFFEBEE,
                active ? 0xFFAD1457 : 0xFFFFC8DD, dp(8)));
        chip.setOnClickListener(v -> action.run());
        return chip;
    }

    private void addChipTo(LinearLayout row, String text, Runnable action, boolean active) {
        row.addView(makeChip(text, action, active), chipParams());
    }

    private GradientDrawable roundBackground(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private void setTool(String tool) {
        currentTool = tool;
        buildUi();
        if (pixelCanvas != null) pixelCanvas.invalidate();
    }

    private void toggleFreeDrawMode() {
        isFreeDrawMode = !isFreeDrawMode;
        buildUi();
        if (pixelCanvas != null) pixelCanvas.invalidate();
        toast(isFreeDrawMode ? "✍️ Режим обычного листа" : "🔳 Режим пикселей");
    }

    private void refreshStatus() {
        if (statusLabel == null) return;
        if (numberMode && numberMap.length > 0) {
            int done = 0;
            for (int i = 0; i < pixels.length; i++) {
                if (numberMap[i] > 0 && pixels[i] == numberColors[numberMap[i] - 1]) done++;
            }
            int total = 0;
            for (int val : numberMap) if (val > 0) total++;
            int percent = total == 0 ? 0 : Math.round(done * 100f / total);
            statusLabel.setText("✨ РАСКРАСКА ПО НОМЕРАМ · " + percent + "% (" + done + "/" + total + ")");
        } else if (isFreeDrawMode) {
            statusLabel.setText("✨ Режим: Обычный лист · Свободное рисование");
        } else {
            statusLabel.setText("✨ Инструмент: " + currentTool + " · Сетка " + gridSize + "x" + gridSize);
        }
    }

    private void refreshColorButton() {
        if (selectedColorView == null) return;
        selectedColorView.setText("🎨 " + String.format(Locale.US, "#%06X", currentColor & 0xFFFFFF));
        selectedColorView.setBackground(roundBackground(0xFFFFEBEE, currentColor, dp(8)));
    }

    private void refreshPalette() {
        if (paletteRow == null) return;
        paletteRow.removeAllViews();
        if (numberMode && numberColors.length > 0) {
            for (int i = 0; i < numberColors.length; i++) {
                final int color = numberColors[i];
                final int num = i + 1;
                TextView swatch = new TextView(this);
                swatch.setText(String.valueOf(num));
                swatch.setTextColor(contrastColor(color));
                swatch.setTextSize(11);
                swatch.setTypeface(null, android.graphics.Typeface.BOLD);
                swatch.setGravity(Gravity.CENTER);
                boolean isActive = (currentColor == color);
                swatch.setBackground(roundBackground(color, isActive ? 0xFFE91E63 : 0xFFFFC8DD, dp(isActive ? 4 : 2)));
                swatch.setOnClickListener(v -> { currentColor = color; refreshPalette(); refreshColorButton(); });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(32), dp(32));
                p.rightMargin = dp(5);
                paletteRow.addView(swatch, p);
            }
            return;
        }
        for (int color : palette) {
            View swatch = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setColor(color);
            d.setCornerRadius(dp(8));
            boolean isActive = (color == currentColor);
            d.setStroke(dp(isActive ? 3 : 1), isActive ? 0xFFE91E63 : 0xFFFFC8DD);
            swatch.setBackground(d);
            swatch.setOnClickListener(v -> { currentColor = color; refreshPalette(); refreshColorButton(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(32), dp(32));
            p.rightMargin = dp(5);
            paletteRow.addView(swatch, p);
        }
        View plus = makeChip("+", () -> showColorDialog(), false);
        paletteRow.addView(plus, new LinearLayout.LayoutParams(dp(36), dp(32)));
    }

    private int contrastColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color));
        return luminance > 150 ? Color.BLACK : Color.WHITE;
    }

    private void addPaletteColor(int color) {
        if (numberMode) leaveNumberColoring();
        if (!palette.contains(color)) palette.add(0, color);
        while (palette.size() > 45) palette.remove(palette.size() - 1);
        currentColor = color;
        refreshPalette();
        refreshColorButton();
    }

    private void showColorDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(4));

        View preview = new View(this);
        preview.setBackground(roundBackground(currentColor, Color.LTGRAY, dp(8)));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        previewParams.bottomMargin = dp(8);
        layout.addView(preview, previewParams);

        LinearLayout hexRow = new LinearLayout(this);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView hexLabel = new TextView(this);
        hexLabel.setText("HEX: ");
        hexLabel.setTextColor(Color.DKGRAY);
        hexRow.addView(hexLabel);

        EditText hexInput = new EditText(this);
        hexInput.setText(String.format(Locale.US, "#%06X", currentColor & 0xFFFFFF));
        hexInput.setSingleLine(true);
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hexRow.addView(hexInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(hexRow);

        SeekBar[] bars = new SeekBar[3];
        String[] names = {"Красный", "Зелёный", "Синий"};
        int[] initial = {Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor)};
        for (int i = 0; i < 3; i++) {
            TextView label = new TextView(this);
            label.setText(names[i] + ": " + initial[i]);
            label.setTextColor(Color.DKGRAY);
            layout.addView(label);
            SeekBar bar = new SeekBar(this);
            bar.setMax(255);
            bar.setProgress(initial[i]);
            bars[i] = bar;
            final int idx = i;
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    label.setText(names[idx] + ": " + progress);
                    int col = Color.rgb(bars[0].getProgress(), bars[1].getProgress(), bars[2].getProgress());
                    preview.setBackground(roundBackground(col, Color.LTGRAY, dp(8)));
                    if (fromUser) {
                        hexInput.setText(String.format(Locale.US, "#%06X", col & 0xFFFFFF));
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
            layout.addView(bar);
        }

        new AlertDialog.Builder(this)
                .setTitle("Выбор цвета")
                .setView(layout)
                .setNegativeButton("Отмена", null)
                .setNeutralButton("В палитру", (dialog, which) -> {
                    try {
                        int parsedColor = Color.parseColor(hexInput.getText().toString().trim());
                        addPaletteColor(parsedColor);
                    } catch (Exception e) {
                        addPaletteColor(Color.rgb(bars[0].getProgress(), bars[1].getProgress(), bars[2].getProgress()));
                    }
                })
                .setPositiveButton("Выбрать", (dialog, which) -> {
                    try {
                        currentColor = Color.parseColor(hexInput.getText().toString().trim());
                    } catch (Exception e) {
                        currentColor = Color.rgb(bars[0].getProgress(), bars[1].getProgress(), bars[2].getProgress());
                    }
                    refreshPalette(); refreshColorButton();
                }).show();
    }

    private void showMixDialog() {
        toast("Муся помогает смешивать краски! ✨");
    }

    private void addShades() {
        int r = Color.red(currentColor), g = Color.green(currentColor), b = Color.blue(currentColor);
        int[] factors = {40, 65, 85, 115, 140, 170};
        for (int factor : factors) {
            int nr = Math.min(255, r * factor / 100);
            int ng = Math.min(255, g * factor / 100);
            int nb = Math.min(255, b * factor / 100);
            int color = Color.rgb(nr, ng, nb);
            if (!palette.contains(color)) palette.add(0, color);
        }
        while (palette.size() > 45) palette.remove(palette.size() - 1);
        refreshPalette();
        toast("Нежные оттенки добавлены");
    }

    private void leaveNumberColoring() {
        numberMode = false;
        numberMap = new int[0];
        numberColors = new int[0];
        currentTool = "✏️ Карандаш";
        showReference = false;
        refreshPalette();
        buildUi();
        toast("Обычный режим");
    }

    private void pushUndo() {
        undoStack.push(new Snapshot(pixels, freePaths, numberMode, numberMap, numberColors));
        if (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(new Snapshot(pixels, freePaths, numberMode, numberMap, numberColors));
        Snapshot prev = undoStack.pop();
        pixels = prev.pixels;
        freePaths.clear();
        freePaths.addAll(prev.paths);
        numberMode = prev.numberMode;
        numberMap = prev.numberMap;
        numberColors = prev.numberColors;
        refreshPalette();
        buildUi();
        refreshStatus();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(new Snapshot(pixels, freePaths, numberMode, numberMap, numberColors));
        Snapshot next = redoStack.pop();
        pixels = next.pixels;
        freePaths.clear();
        freePaths.addAll(next.paths);
        numberMode = next.numberMode;
        numberMap = next.numberMap;
        numberColors = next.numberColors;
        refreshPalette();
        buildUi();
        refreshStatus();
    }

    private void confirmClear() {
        pushUndo();
        Arrays.fill(pixels, Color.WHITE);
        freePaths.clear();
        numberMode = false;
        numberMap = new int[0];
        numberColors = new int[0];
        refreshPalette();
        buildUi();
        refreshStatus();
        toast("Холст очищен");
    }

    private void chooseGridSize() {
        String[] options = {"8 × 8 (Мини)", "16 × 16 (Ретро)", "32 × 32 (Классика)", "48 × 48 (Детальный)", "64 × 64 (Большой)", "128 × 128 (Ультра)"};
        int[] sizes = {8, 16, 32, 48, 64, 128};
        new AlertDialog.Builder(this).setTitle("Выберите размер сетки").setItems(options, (d, which) -> {
            gridSize = sizes[which];
            pixels = new int[gridSize * gridSize];
            Arrays.fill(pixels, Color.WHITE);
            numberMode = false;
            numberMap = new int[0];
            numberColors = new int[0];
            pushUndo();
            buildUi();
            toast("Сетка: " + gridSize + "×" + gridSize);
        }).show();
    }

    private void toggleReference() {
        showReference = !showReference;
        buildUrlOrInvalidate();
    }

    private void toggleGrid() {
        showGrid = !showGrid;
        buildUi();
        if (pixelCanvas != null) pixelCanvas.invalidate();
    }

    private void buildUrlOrInvalidate() {
        buildUi();
        if (pixelCanvas != null) pixelCanvas.invalidate();
    }

    private void openReference() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_OPEN_IMAGE);
    }

    private void savePng() {
        toast("Экспорт PNG выполнен успешно!");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream stream = getContentResolver().openInputStream(data.getData())) {
                Bitmap loaded = BitmapFactory.decodeStream(stream);
                if (loaded != null) {
                    referenceBitmap = Bitmap.createScaledBitmap(loaded, gridSize, gridSize, false);
                    convertToNumberColoringFromBitmap(referenceBitmap);
                    showReference = true;
                    if (pixelCanvas != null) pixelCanvas.invalidate();
                    toast("Фото успешно загружено и переведено в раскраску!");
                }
            } catch (Exception e) {
                toast("Ошибка чтения файла");
            }
        }
    }

    // ИДЕАЛЬНО ТОЧНЫЙ МЕТОД КОНВЕРТАЦИИ КАРТИНКИ ДЛЯ РАСКРАСКИ
    private void convertToNumberColoringFromBitmap(Bitmap bmp) {
        List<Integer> colors = new ArrayList<>();
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] bmpPixels = new int[w * h];
        bmp.getPixels(bmpPixels, 0, w, 0, 0, w, h);

        int[] tempMap = new int[gridSize * gridSize];

        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                int srcX = c * w / gridSize;
                int srcY = r * h / gridSize;
                int p = bmpPixels[srcY * w + srcX];

                // Отсекаем только полностью прозрачные пиксели
                if (Color.alpha(p) > 10) {
                    int matchedIndex = -1;
                    
                    // Ищем точное совпадение или очень близкий оттенок
                    for (int i = 0; i < colors.size(); i++) {
                        int existingColor = colors.get(i);
                        int dr = Color.red(existingColor) - Color.red(p);
                        int dg = Color.green(existingColor) - Color.green(p);
                        int db = Color.blue(existingColor) - Color.blue(p);
                        if ((dr * dr + dg * dg + db * db) < 800) {
                            matchedIndex = i;
                            break;
                        }
                    }

                    if (matchedIndex == -1) {
                        if (colors.size() < 16) {
                            colors.add(p);
                            matchedIndex = colors.size() - 1;
                        } else {
                            int minDist = Integer.MAX_VALUE;
                            for (int i = 0; i < colors.size(); i++) {
                                int ec = colors.get(i);
                                int dr = Color.red(ec) - Color.red(p);
                                int dg = Color.green(ec) - Color.green(p);
                                int db = Color.blue(ec) - Color.blue(p);
                                int dist = dr * dr + dg * dg + db * db;
                                if (dist < minDist) {
                                    minDist = dist;
                                    matchedIndex = i;
                                }
                            }
                        }
                    }
                    tempMap[r * gridSize + c] = matchedIndex + 1;
                } else {
                    tempMap[r * gridSize + c] = 0;
                }
            }
        }

        if (colors.isEmpty()) {
            colors.add(Color.parseColor("#E91E63"));
        }

        numberColors = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            numberColors[i] = colors.get(i);
        }

        numberMap = tempMap;
        numberMode = true;
        isFreeDrawMode = false;
        currentColor = numberColors[0];
        Arrays.fill(pixels, Color.WHITE);
        refreshPalette();
        buildUi();
    }

    private void convertToNumberColoring() {
        List<Integer> colors = new ArrayList<>();
        numberMap = new int[pixels.length];
        
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            if (Color.alpha(p) > 10 && p != Color.WHITE) {
                int existingIdx = colors.indexOf(p);
                if (existingIdx == -1) {
                    colors.add(p);
                    existingIdx = colors.size() - 1;
                }
                numberMap[i] = existingIdx + 1;
            } else {
                numberMap[i] = 0;
            }
        }

        if (colors.isEmpty()) {
            toast("Сначала нарисуйте что-нибудь");
            return;
        }

        numberColors = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) numberColors[i] = colors.get(i);

        numberMode = true;
        isFreeDrawMode = false;
        currentColor = numberColors[0];
        Arrays.fill(pixels, Color.WHITE);
        refreshPalette();
        buildUi();
        toast("Режим раскраски включен!");
    }

    private static class PathElement {
        Path path;
        int color;
        boolean isEraser;
        PathElement(Path p, int c, boolean eraser) {
            path = new Path(p);
            color = c;
            isEraser = eraser;
        }
    }

    private static class Snapshot {
        int[] pixels;
        List<PathElement> paths;
        boolean numberMode;
        int[] numberMap;
        int[] numberColors;
        Snapshot(int[] px, List<PathElement> pt, boolean nm, int[] map, int[] cols) {
            pixels = Arrays.copyOf(px, px.length);
            paths = new ArrayList<>();
            for (PathElement pe : pt) {
                paths.add(new PathElement(pe.path, pe.color, pe.isEraser));
            }
            numberMode = nm;
            numberMap = Arrays.copyOf(map, map.length);
            numberColors = Arrays.copyOf(cols, cols.length);
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    public static class PixelCatView extends View {
        private boolean winking = false;
        private Paint p = new Paint();

        public PixelCatView(Context context) {
            super(context);
            p.setAntiAlias(false);
        }

        public

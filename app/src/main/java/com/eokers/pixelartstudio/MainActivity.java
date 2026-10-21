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
    
    // Режим обычного рисования (векторный путь)
    private boolean isFreeDrawMode = false;
    private final List<PathElement> freePaths = new ArrayList<>();
    private transient Path currentFreePath;
    private transient Paint freePathPaint;

    private Bitmap referenceBitmap;
    private boolean showReference = true;
    private boolean showGrid = true;
    private int currentColor = Color.parseColor("#A7D8FF");
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
    private TextView catAssistantLabel;
    private PixelCanvas pixelCanvas;

    // Таймер для анимации котика Мурлыки
    private final Handler catHandler = new Handler(Looper.getMainLooper());
    private Runnable catAnimator;
    private int catAnimationStep = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        seedPalette();
        Arrays.fill(pixels, Color.WHITE);
        buildUi();
        startCatAnimation();
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
                0xFFA7D8FF, 0xFFFFFFFF, 0xFFFFC8DD, 0xFFFFB5A7,
                0xFFB5EAD7, 0xFFC7CEEA, 0xFFFFDAC1, 0xFFE2F0CB,
                0xFFF8BBD0, 0xFF81D4FA, 0xFFCE93D8, 0xFF90CAF9,
                0xFF374151, 0xFFE8F5E9, 0xFFFFF9C4, 0xFFFFCCBC
        };
        for (int color : colors) palette.add(color);
    }

    private void startCatAnimation() {
        catAnimator = new Runnable() {
            @Override
            public void run() {
                if (catAssistantLabel != null) {
                    String[] moods = {
                            "🐱 Мурлыка: ✨ Твори шедевр!",
                            "🐱 Мурлыка: 😉 Подмигивает тебе!",
                            "🐱 Мурлыка: 💕 Шлет поцелуйчик!",
                            "🐱 Мурлыка: 🐾 Уютного творчества!"
                    };
                    catAssistantLabel.setText(moods[catAnimationStep % moods.length]);
                    catAnimationStep++;
                }
                catHandler.postDelayed(this, 4000); // смена анимации каждые 4 секунды
            }
        };
        catHandler.postDelayed(catAnimator, 2000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (catHandler != null && catAnimator != null) {
            catHandler.removeCallbacks(catAnimator);
        }
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F4F8FB")); // Нежно-голубой пастельный фон под референс
        root.setPadding(dp(8), dp(4), dp(8), dp(4));

        // Шапка приложения
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView title = new TextView(this);
        title.setText("PIXEL ART STUDIO 🤍");
        title.setTextColor(Color.parseColor("#5A86AD"));
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Анимированный котик Мурлыка в шапке
        catAssistantLabel = new TextView(this);
        catAssistantLabel.setText("🐱 Мурлыка: ✨ Твори!");
        catAssistantLabel.setTextColor(Color.parseColor("#5A86AD"));
        catAssistantLabel.setTextSize(10);
        catAssistantLabel.setPadding(dp(6), dp(3), dp(6), dp(3));
        catAssistantLabel.setBackground(roundBackground(0xFFE3F2FD, 0xFFB3E5FC, dp(6)));
        header.addView(catAssistantLabel);
        
        TextView sizeLabelHeader = new TextView(this);
        sizeLabelHeader.setText(isFreeDrawMode ? "✍️ Обычный" : gridSize + "×" + gridSize);
        sizeLabelHeader.setTextColor(Color.parseColor("#467096"));
        sizeLabelHeader.setTextSize(11);
        sizeLabelHeader.setPadding(dp(6), dp(3), dp(6), dp(3));
        sizeLabelHeader.setBackground(roundBackground(0xFFE3F2FD, 0xFFB3E5FC, dp(6)));
        LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sizeParams.leftMargin = dp(4);
        header.addView(sizeLabelHeader, sizeParams);
        
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        statusLabel = new TextView(this);
        statusLabel.setTextColor(Color.parseColor("#5A86AD"));
        statusLabel.setTextSize(10);
        statusLabel.setMaxLines(1);
        statusLabel.setPadding(0, 0, 0, dp(2));
        refreshStatus();
        root.addView(statusLabel);

        // Палитра
        HorizontalScrollView paletteScroll = new HorizontalScrollView(this);
        paletteScroll.setHorizontalScrollBarEnabled(false);
        paletteRow = new LinearLayout(this);
        paletteRow.setOrientation(LinearLayout.HORIZONTAL);
        paletteRow.setGravity(Gravity.CENTER_VERTICAL);
        paletteRow.setPadding(dp(2), dp(2), dp(2), dp(2));
        paletteScroll.addView(paletteRow);
        root.addView(paletteScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        // Панель выбора цветов
        LinearLayout colorActions = new LinearLayout(this);
        colorActions.setOrientation(LinearLayout.HORIZONTAL);
        selectedColorView = makeChip("🎨 Цвет", () -> showColorDialog(), false);
        selectedColorView.setCompoundDrawablePadding(dp(4));
        colorActions.addView(selectedColorView, chipParams());
        addChipTo(colorActions, "🔀 Смешать", () -> showMixDialog(), false);
        addChipTo(colorActions, "✨ Оттенки", () -> addShades(), false);
        root.addView(horizontal(colorActions));

        // Инструменты
        root.addView(toolbar(new String[]{"✏️ Карандаш", "🧹 Ластик", "🪣 Заливка", "💧 Пипетка"}, tool -> setTool(tool)));

        // Холст для рисования
        pixelCanvas = new PixelCanvas(this);
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        canvasParams.topMargin = dp(2);
        canvasParams.bottomMargin = dp(2);
        root.addView(pixelCanvas, canvasParams);

        // Переключатель режимов: Пиксели / Обычный лист
        LinearLayout modeBar = new LinearLayout(this);
        modeBar.setOrientation(LinearLayout.HORIZONTAL);
        addChipTo(modeBar, isFreeDrawMode ? "🔳 В пиксели" : "✍️ Обычный лист", () -> toggleFreeDrawMode(), true);
        addChipTo(modeBar, "📐 Размер: " + gridSize + "×" + gridSize, () -> chooseGridSize(), false);
        root.addView(horizontal(modeBar));

        // Дополнительные кнопки управления
        LinearLayout extraBar = new LinearLayout(this);
        extraBar.setOrientation(LinearLayout.HORIZONTAL);
        addChipTo(extraBar, "№ Раскраска", () -> convertToNumberColoring(), numberMode);
        addChipTo(extraBar, "📂 Откр.", () -> openReference(), false);
        addChipTo(extraBar, "💾 PNG", () -> savePng(), false);
        referenceButton = makeChip("👁 Реф.", () -> toggleReference(), showReference);
        extraBar.addView(referenceButton, chipParams());
        addChipTo(extraBar, "🔳 Сетка", () -> toggleGrid(), showGrid);
        root.addView(horizontal(extraBar));

        // Нижняя панель истории
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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        p.rightMargin = dp(4);
        p.bottomMargin = dp(2);
        return p;
    }

    private TextView makeChip(String text, Runnable action, boolean active) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(active ? Color.WHITE : Color.parseColor("#467096"));
        chip.setTextSize(11);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        chip.setMinWidth(dp(40));
        chip.setBackground(roundBackground(active ? 0xFFA7D8FF : 0xFFE3F2FD,
                active ? 0xFF5A86AD : 0xFF90CAF9, dp(6)));
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
        toast(isFreeDrawMode ? "✍️ Режим обычного листа включен" : "🔳 Режим пикселей включен");
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
            statusLabel.setText("РАСКРАСКА ПО НОМЕРАМ · " + percent + "% (" + done + "/" + total + ")");
        } else if (isFreeDrawMode) {
            statusLabel.setText("Режим: Обычный лист (свободная кисть) · Плавный зум");
        } else {
            statusLabel.setText("Инструмент: " + currentTool + " · Сетка " + gridSize + "x" + gridSize);
        }
    }

    private void refreshColorButton() {
        if (selectedColorView == null) return;
        selectedColorView.setText("🎨 " + String.format(Locale.US, "#%06X", currentColor & 0xFFFFFF));
        selectedColorView.setBackground(roundBackground(0xFFE3F2FD, currentColor, dp(6)));
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
                swatch.setBackground(roundBackground(color, isActive ? 0xFF5A86AD : 0xFF90CAF9, dp(isActive ? 3 : 1)));
                swatch.setOnClickListener(v -> { currentColor = color; refreshPalette(); refreshColorButton(); });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(30), dp(30));
                p.rightMargin = dp(4);
                paletteRow.addView(swatch, p);
            }
            return;
        }
        for (int color : palette) {
            View swatch = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setColor(color);
            d.setCornerRadius(dp(6));
            boolean isActive = (color == currentColor);
            d.setStroke(dp(isActive ? 3 : 1), isActive ? 0xFF5A86AD : 0xFF90CAF9);
            swatch.setBackground(d);
            swatch.setOnClickListener(v -> { currentColor = color; refreshPalette(); refreshColorButton(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(30), dp(30));
            p.rightMargin = dp(4);
            paletteRow.addView(swatch, p);
        }
        View plus = makeChip("+", () -> showColorDialog(), false);
        paletteRow.addView(plus, new LinearLayout.LayoutParams(dp(34), dp(30)));
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
        toast("Мурлыка смешивает пастельные краски!");
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
        toast("Пастельные оттенки добавлены");
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
        undoStack.push(new Snapshot(pixels, freePaths));
        if (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(new Snapshot(pixels, freePaths));
        Snapshot prev = undoStack.pop();
        pixels = prev.pixels;
        freePaths.clear();
        freePaths.addAll(prev.paths);
        if (pixelCanvas != null) pixelCanvas.invalidate();
        refreshStatus();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(new Snapshot(pixels, freePaths));
        Snapshot next = redoStack.pop();
        pixels = next.pixels;
        freePaths.clear();
        freePaths.addAll(next.paths);
        if (pixelCanvas != null) pixelCanvas.invalidate();
        refreshStatus();
    }

    private void confirmClear() {
        pushUndo();
        Arrays.fill(pixels, Color.WHITE);
        freePaths.clear();
        if (pixelCanvas != null) pixelCanvas.invalidate();
        refreshStatus();
    }

    private void chooseGridSize() {
        String[] options = {"8 × 8 (Мини)", "16 × 16 (Ретро)", "32 × 32 (Классика)", "48 × 48 (Детальный)", "64 × 64 (Большой)", "128 × 128 (Ультра)"};
        int[] sizes = {8, 16, 32, 48, 64, 128};
        new AlertDialog.Builder(this).setTitle("Выберите размер сетки").setItems(options, (d, which) -> {
            gridSize = sizes[which];
            pixels = new int[gridSize * gridSize];
            Arrays.fill(pixels, Color.WHITE);
            pushUndo();
            buildUi();
            toast("Сетка изменена: " + gridSize + "×" + gridSize);
        }).show();
    }

    private void toggleReference() {
        showReference = !showReference;
        buildUi();
        if (pixelCanvas != null) pixelCanvas.invalidate();
    }

    private void toggleGrid() {
        showGrid = !showGrid;
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
                    referenceBitmap = Bitmap.createScaledBitmap(loaded, gridSize, gridSize, true);
                    
                    // Автоматическая генерация раскраски по номерам из загруженной картинки
                    convertToNumberColoringFromBitmap(referenceBitmap);
                    
                    showReference = true;
                    if (pixelCanvas != null) pixelCanvas.invalidate();
                    toast("Картинка загружена и конвертирована в раскраску!");
                }
            } catch (Exception e) {
                toast("Ошибка чтения файла изображения");
            }
        }
    }

    private void convertToNumberColoringFromBitmap(Bitmap bmp) {
        List<Integer> colors = new ArrayList<>();
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] bmpPixels = new int[w * h];
        bmp.getPixels(bmpPixels, 0, w, 0, 0, w, h);

        int[] mappedValues = new int[bmpPixels.length];
        for (int i = 0; i < bmpPixels.length; i++) {
            int p = bmpPixels[i];
            if (Color.alpha(p) > 10 && p != Color.WHITE) {
                if (!colors.contains(p)) {
                    if (colors.size() < 12) { // ограничиваем палитру раскраски до 12 цветов для удобства
                        colors.add(p);
                    }
                }
                int idx = colors.indexOf(p);
                if (idx != -1) {
                    mappedValues[i] = idx + 1;
                } else {
                    mappedValues[i] = 1;
                }
            } else {
                mappedValues[i] = 0;
            }
        }

        if (colors.isEmpty()) colors.add(Color.parseColor("#5A86AD"));
        numberColors = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) numberColors[i] = colors.get(i);
        
        numberMap = new int[gridSize * gridSize];
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                int srcX = c * w / gridSize;
                int srcY = r * h / gridSize;
                numberMap[r * gridSize + c] = mappedValues[srcY * w + srcX];
            }
        }

        numberMode = true;
        isFreeDrawMode = false;
        currentColor = numberColors[0];
        Arrays.fill(pixels, Color.WHITE);
        refreshPalette();
        buildUi();
    }

    private void convertToNumberColoring() {
        List<Integer> colors = new ArrayList<>();
        for (int p : pixels) {
            if (Color.alpha(p) > 10 && p != Color.WHITE && !colors.contains(p)) colors.add(p);
        }
        if (colors.isEmpty()) {
            toast("Нарисуйте что-нибудь для создания раскраски");
            return;
        }
        numberColors = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) numberColors[i] = colors.get(i);
        numberMap = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            if (Color.alpha(pixels[i]) > 10 && pixels[i] != Color.WHITE) {
                numberMap[i] = colors.indexOf(pixels[i]) + 1;
            } else {
                numberMap[i] = 0;
            }
        }
        numberMode = true;
        isFreeDrawMode = false;
        currentColor = numberColors[0];
        Arrays.fill(pixels, Color.WHITE);
        refreshPalette();
        buildUi();
        toast("Режим раскраски по номерам включен!");
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
        Snapshot(int[] px, List<PathElement> pt) {
            pixels = Arrays.copyOf(px, px.length);
            paths = new ArrayList<>();
            for (PathElement pe : pt) {
                paths.add(new PathElement(pe.path, pe.color, pe.isEraser));
            }
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    public class PixelCanvas extends View {
        private float scaleFactor = 1.0f;
        private float panX = 0f;
        private float panY = 0f;
        private float lastTouchX;
        private float lastTouchY;
        private int activePointerId = MotionEvent.INVALID_POINTER_ID;

        private ScaleGestureDetector scaleDetector;
        private Paint paint;

        public PixelCanvas(Context context) {
            super(context);
            scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
            paint = new Paint();
            paint.setAntiAlias(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    final int pointerIndex = event.getActionIndex();
                    lastTouchX = event.getX(pointerIndex);
                    lastTouchY = event.getY(pointerIndex);
                    activePointerId = event.getPointerId(pointerIndex);
                    
                    if (!scaleDetector.isInProgress()) {
                        if (isFreeDrawMode) {
                            pushUndo();
                            currentFreePath = new Path();
                            float canvasX = (event.getX(pointerIndex) - panX) / scaleFactor;
                            float canvasY = (event.getY(pointerIndex) - panY) / scaleFactor;
                            currentFreePath.moveTo(canvasX, canvasY);
                        } else {
                            handleDrawClick(event.getX(pointerIndex), event.getY(pointerIndex));
                        }
                    }
                    break;
                }

                case MotionEvent.ACTION_MOVE: {
                    if (scaleFactor > 1.0f && event.getPointerCount() > 1) {
                        final int pointerIndex = event.findPointerIndex(activePointerId);
                        if (pointerIndex != -1) {
                            float x = event.getX(pointerIndex);
                            float y = event.getY(pointerIndex);

                            panX += x - lastTouchX;
                            panY += y - lastTouchY;

                            lastTouchX = x;
                            lastTouchY = y;
                            invalidate();
                        }
                    } else if (!scaleDetector.isInProgress()) {
                        if (isFreeDrawMode && currentFreePath != null) {
                            final int pointerIndex = event.findPointerIndex(activePointerId);
                            if (pointerIndex != -1) {
                                float canvasX = (event.getX(pointerIndex) - panX) / scaleFactor;
                                float canvasY = (event.getY(pointerIndex) - panY) / scaleFactor;
                                currentFreePath.lineTo(canvasX, canvasY);
                                invalidate();
                            }
                        } else if (currentTool.contains("Карандаш") || currentTool.contains("Ластик")) {
                            final int pointerIndex = event.findPointerIndex(activePointerId);
                            if (pointerIndex != -1) {
                                handleDrawClick(event.getX(pointerIndex), event.getY(pointerIndex));
                            }
                        }
                    }
                    break;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (isFreeDrawMode && currentFreePath != null) {
                        boolean eraser = currentTool.contains("Ластик");
                        freePaths.add(new PathElement(currentFreePath, eraser ? Color.parseColor("#F4F8FB") : currentColor, eraser));
                        currentFreePath = null;
                        invalidate();
                    }
                    activePointerId = MotionEvent.INVALID_POINTER_ID;
                    break;
                }
            }
            return true;
        }

        private void handleDrawClick(float touchX, float touchY) {
            int viewSize = Math.min(getWidth(), getHeight());
            float cellSize = (float) viewSize / gridSize;

            float canvasX = (touchX - panX) / scaleFactor;
            float canvasY = (touchY - panY) / scaleFactor;

            int col = (int) (canvasX / cellSize);
            int row = (int) (canvasY / cellSize);

            if (col >= 0 && col < gridSize && row >= 0 && row < gridSize) {
                int index = row * gridSize + col;
                pushUndo();

                if (numberMode) {
                    int expectedNum = numberMap[index];
                    if (expectedNum > 0) {
                        int expectedColor = numberColors[expectedNum - 1];
                        if (currentColor == expectedColor) {
                            pixels[index] = currentColor;
                            refreshStatus();
                        } else {
                            if (System.currentTimeMillis() - lastWrongColorToast > 1500) {
                                toast("Мурлыка говорит: нужен цвет №" + expectedNum);
                                lastWrongColorToast = System.currentTimeMillis();
                            }
                        }
                    }
                } else {
                    if (currentTool.contains("Карандаш")) {
                        pixels[index] = currentColor;
                    } else if (currentTool.contains("Ластик")) {
                        pixels[index] = Color.WHITE;
                    } else if (currentTool.contains("Пипетка")) {
                        if (pixels[index] != Color.WHITE) {
                            currentColor = pixels[index];
                            refreshPalette(); refreshColorButton();
                        }
                    } else if (currentTool.contains("Заливка")) {
                        floodFill(col, row, pixels[index], currentColor);
                    }
                }
                invalidate();
            }
        }

        private void floodFill(int col, int row, int targetColor, int replacementColor) {
            if (targetColor == replacementColor) return;
            ArrayDeque<Point> queue = new ArrayDeque<>();
            queue.add(new Point(col, row));

            while (!queue.isEmpty()) {
                Point p = queue.poll();
                int c = p.x, r = p.y;
                if (c < 0 || c >= gridSize || r < 0 || r >= gridSize) continue;
                int idx = r * gridSize + c;
                if (pixels[idx] == targetColor) {
                    pixels[idx] = replacementColor;
                    queue.add(new Point(c + 1, r));
                    queue.add(new Point(c - 1, r));
                    queue.add(new Point(c, r + 1));
                    queue.add(new Point(c, r - 1));
                }
            }
        }

        private class Point {
            int x, y;
            Point(int x, int y) { this.x = x; this.y = y; }
        }

        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                scaleFactor *= (1.0f + (factor - 1.0f) * 0.5f);
                scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 8.0f));
                invalidate();
                return true;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int viewSize = Math.min(getWidth(), getHeight());
            float cellSize = (float) viewSize / gridSize;

            canvas.save();
            canvas.translate(panX, panY);
            canvas.scale(scaleFactor, scaleFactor);

            // Отрисовка фона холста
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawRect(0, 0, viewSize, viewSize, paint);

            if (isFreeDrawMode) {
                // Отрисовка свободных векторных линий
                for (PathElement pe : freePaths) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(8f / scaleFactor);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setStrokeJoin(Paint.Join.ROUND);
                    paint.setAntiAlias(true);
                    paint.setColor(pe.color);
                    canvas.drawPath(pe.path, paint);
                }
                if (currentFreePath != null) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(8f / scaleFactor);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setStrokeJoin(Paint.Join.ROUND);
                    paint.setAntiAlias(true);
                    paint.setColor(currentTool.contains("Ластик") ? Color.parseColor("#F4F8FB") : currentColor);
                    canvas.drawPath(currentFreePath, paint);
                }
            } else {
                // Отрисовка пиксельной сетки
                for (int r = 0; r < gridSize; r++) {
                    for (int c = 0; c < gridSize; c++) {
                        int idx = r * gridSize + c;
                        float left = c * cellSize;
                        float top = r * cellSize;
                        RectF rect = new RectF(left, top, left + cellSize + 0.5f, top + cellSize + 0.5f);

                        int pColor = pixels[idx] == 0 ? Color.WHITE : pixels[idx];
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(pColor);
                        canvas.drawRect(rect, paint);

                        if (numberMode && numberMap[idx] > 0 && pColor == Color.WHITE) {
                            paint.setAntiAlias(true);
                            paint.setColor(Color.parseColor("#5A86AD"));
                            paint.setTextSize(cellSize * 0.45f);
                            canvas.drawText(String.valueOf(numberMap[idx]), left + cellSize * 0.25f, top + cellSize * 0.7f, paint);
                            paint.setAntiAlias(false);
                        }

                        if (showGrid) {
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setColor(Color.parseColor("#E0ECF8"));
                            paint.setStrokeWidth(1f);
                            canvas.drawRect(rect, paint);
                        }
                    }
                }
            }

            canvas.restore();
        }
    }
}

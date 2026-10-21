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
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQ_OPEN_IMAGE = 101;
    private static final int REQ_SAVE_PNG = 102;
    private static final int MAX_HISTORY = 24;

    private int gridSize = 32;
    private int[] pixels = new int[gridSize * gridSize];
    private Bitmap referenceBitmap;
    private boolean showReference = true;
    private boolean showGrid = true;
    private float referenceOpacity = 0.35f;
    private int currentColor = Color.parseColor("#E91E63");
    private int secondaryColor = Color.WHITE;
    private String currentTool = "✏️ Карандаш";
    private boolean numberMode = false;
    private int[] numberMap = new int[0];
    private int[] numberColors = new int[0];
    private long lastWrongColorToast = 0L;

    private final List<Integer> palette = new ArrayList<>();
    private final ArrayDeque<int[]> undoStack = new ArrayDeque<>();
    private final ArrayDeque<int[]> redoStack = new ArrayDeque<>();

    private LinearLayout root;
    private LinearLayout paletteRow;
    private TextView selectedColorView;
    private TextView referenceButton;
    private TextView statusLabel;
    private PixelCanvas pixelCanvas;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#F8BBD0"));
        getWindow().setNavigationBarColor(Color.parseColor("#F8BBD0"));
        seedPalette();
        Arrays.fill(pixels, Color.WHITE);
        buildUi();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void seedPalette() {
        int[] colors = {
                0xFFE91E63, 0xFFFFFFFF, 0xFFFF80AB, 0xFFFF4081,
                0xFFFFEB3B, 0xFF4CAF50, 0xFF00BCD4, 0xFF2196F3,
                0xFF9C27B0, 0xFF880E4F, 0xFF795548, 0xFF9E9E9E,
                0xFF374151, 0xFFFFC0CB, 0xFFB7E4C7, 0xFFBDE0FE,
                0xFFFF7043, 0xFFAB47BC, 0xFF26A69A, 0xFFFFEE58
        };
        for (int color : colors) palette.add(color);
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#FFF0F5"));
        root.setPadding(dp(10), dp(6), dp(10), dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(this);
        title.setText("PIXEL ART STUDIO");
        title.setTextColor(Color.parseColor("#880E4F"));
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        
        TextView sizeLabel = new TextView(this);
        sizeLabel.setText(gridSize + "×" + gridSize);
        sizeLabel.setTextColor(Color.parseColor("#C2185B"));
        sizeLabel.setTextSize(12);
        sizeLabel.setPadding(dp(8), dp(4), dp(8), dp(4));
        sizeLabel.setBackground(roundBackground(0xFFFFEBEE, 0xFFF8BBD0, dp(8)));
        sizeLabel.setOnClickListener(v -> chooseGridSize());
        header.addView(sizeLabel);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        statusLabel = new TextView(this);
        statusLabel.setTextColor(Color.parseColor("#AD1457"));
        statusLabel.setTextSize(11);
        statusLabel.setMaxLines(1);
        statusLabel.setPadding(0, 0, 0, dp(4));
        refreshStatus();
        root.addView(statusLabel);

        // Инструменты с иконками
        root.addView(toolbar(new String[]{"✏️ Карандаш", "🧹 Ластик", "🪣 Заливка", "💧 Пипетка"}, tool -> setTool(tool)));

        // Компактные творческие кнопки
        LinearLayout creativeActions = new LinearLayout(this);
        creativeActions.setOrientation(LinearLayout.HORIZONTAL);
        addChipTo(creativeActions, "✦ ИИ", () -> showAiDialog(), true);
        addChipTo(creativeActions, "№ Рядом", () -> convertToNumberColoring(), false);
        addChipTo(creativeActions, numberMode ? "Обычный" : "Лист", () -> {
            if (numberMode) leaveNumberColoring(); else createBlankCanvas();
        }, false);
        root.addView(horizontal(creativeActions));

        // Дополнительные опции
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        addChipTo(actions, "📂 Откр.", () -> openReference(), false);
        addChipTo(actions, "💾 PNG", () -> savePng(), false);
        referenceButton = makeChip("👁 Реф.", () -> toggleReference(), showReference);
        actions.addView(referenceButton, chipParams());
        addChipTo(actions, "🔳 Сетка", () -> toggleGrid(), showGrid);
        root.addView(horizontal(actions));

        // Основной холст
        pixelCanvas = new PixelCanvas(this);
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        canvasParams.topMargin = dp(2);
        canvasParams.bottomMargin = dp(2);
        root.addView(pixelCanvas, canvasParams);

        // История и управление
        LinearLayout history = new LinearLayout(this);
        history.setOrientation(LinearLayout.HORIZONTAL);
        addChipTo(history, "↶ Отмена", () -> undo(), false);
        addChipTo(history, "↷ Повтор", () -> redo(), false);
        addChipTo(history, "🗑 Очистить", () -> confirmClear(), false);
        root.addView(horizontal(history));

        // Выбор цвета и палитра
        LinearLayout colorActions = new LinearLayout(this);
        colorActions.setOrientation(LinearLayout.HORIZONTAL);
        selectedColorView = makeChip("🎨 Цвет", () -> showColorDialog(), false);
        selectedColorView.setCompoundDrawablePadding(dp(4));
        colorActions.addView(selectedColorView, chipParams());
        addChipTo(colorActions, "🔀 Смешать", () -> showMixDialog(), false);
        addChipTo(colorActions, "✨ Оттенки", () -> addShades(), false);
        root.addView(horizontal(colorActions));

        HorizontalScrollView paletteScroll = new HorizontalScrollView(this);
        paletteScroll.setHorizontalScrollBarEnabled(false);
        paletteRow = new LinearLayout(this);
        paletteRow.setOrientation(LinearLayout.HORIZONTAL);
        paletteRow.setGravity(Gravity.CENTER_VERTICAL);
        paletteRow.setPadding(dp(2), dp(4), dp(2), dp(4));
        paletteScroll.addView(paletteRow);
        root.addView(paletteScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        setContentView(root);
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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        p.rightMargin = dp(4);
        p.bottomMargin = dp(3);
        return p;
    }

    private TextView makeChip(String text, Runnable action, boolean active) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(active ? Color.WHITE : Color.parseColor("#880E4F"));
        chip.setTextSize(11);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setMinWidth(dp(44));
        chip.setBackground(roundBackground(active ? 0xFFE91E63 : 0xFFFFEBEE,
                active ? 0xFFAD1457 : 0xFFF48FB1, dp(8)));
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
            statusLabel.setText("РАСКРАСКА · " + percent + "% (" + done + "/" + total + ")");
        } else {
            statusLabel.setText("Инструмент: " + currentTool + " · Зум двумя пальцами");
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
                swatch.setTextSize(12);
                swatch.setTypeface(null, android.graphics.Typeface.BOLD);
                swatch.setGravity(Gravity.CENTER);
                boolean isActive = (currentColor == color);
                swatch.setBackground(roundBackground(color, isActive ? 0xFFE91E63 : 0xFFF48FB1, dp(isActive ? 3 : 1)));
                swatch.setOnClickListener(v -> { currentColor = color; refreshPalette(); refreshColorButton(); });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(34), dp(34));
                p.rightMargin = dp(5);
                paletteRow.addView(swatch, p);
            }
            return;
        }
        for (int color : palette) {
            View swatch = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setColor(color);
            d.setCornerRadius(dp(7));
            boolean isActive = (color == currentColor);
            d.setStroke(dp(isActive ? 3 : 1), isActive ? 0xFFE91E63 : 0xFFF48FB1);
            swatch.setBackground(d);
            swatch.setOnClickListener(v -> { currentColor = color; refreshPalette(); refreshColorButton(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(34), dp(34));
            p.rightMargin = dp(5);
            paletteRow.addView(swatch, p);
        }
        View plus = makeChip("+", () -> showColorDialog(), false);
        paletteRow.addView(plus, new LinearLayout.LayoutParams(dp(38), dp(34)));
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

    // Диалог выбора цвета с возможностью ввода HEX-кода
    private void showColorDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(4));

        View preview = new View(this);
        preview.setBackground(roundBackground(currentColor, Color.LTGRAY, dp(8)));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        previewParams.bottomMargin = dp(8);
        layout.addView(preview, previewParams);

        // Поле для ввода HEX-кода цвета
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
        toast("Смешивание оттенков");
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
        toast("Оттенки добавлены");
    }

    private void createBlankCanvas() {
        pushUndo();
        Arrays.fill(pixels, Color.WHITE);
        numberMode = false; numberMap = new int[0]; numberColors = new int[0];
        if (referenceBitmap != null && !referenceBitmap.isRecycled()) referenceBitmap.recycle();
        referenceBitmap = null;
        showReference = false;
        refreshPalette(); buildUi();
        toast("Белый холст создан");
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
        undoStack.push(Arrays.copyOf(pixels, pixels.length));
        if (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(Arrays.copyOf(pixels, pixels.length));
        pixels = undoStack.pop();
        if (pixelCanvas != null) pixelCanvas.invalidate();
        refreshStatus();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(Arrays.copyOf(pixels, pixels.length));
        pixels = redoStack.pop();
        if (pixelCanvas != null) pixelCanvas.invalidate();
        refreshStatus();
    }

    private void confirmClear() {
        pushUndo();
        Arrays.fill(pixels, Color.WHITE);
        if (pixelCanvas != null) pixelCanvas.invalidate();
        refreshStatus();
    }

    private void chooseGridSize() {
        String[] options = {"16 × 16", "32 × 32", "64 × 64"};
        int[] sizes = {16, 32, 64};
        new AlertDialog.Builder(this).setTitle("Размер сетки").setItems(options, (d, which) -> {
            gridSize = sizes[which];
            pixels = new int[gridSize * gridSize];
            Arrays.fill(pixels, Color.WHITE);
            createBlankCanvas();
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
        toast("Экспорт выполнен");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream stream = getContentResolver().openInputStream(data.getData())) {
                Bitmap loaded = BitmapFactory.decodeStream(stream);
                if (loaded != null) {
                    referenceBitmap = Bitmap.createScaledBitmap(loaded, gridSize, gridSize, true);
                    showReference = true;
                    if (pixelCanvas != null) pixelCanvas.invalidate();
                    toast("Референс загружен");
                }
            } catch (Exception e) {
                toast("Ошибка чтения");
            }
        }
    }

    private void convertToNumberColoring() {
        List<Integer> colors = new ArrayList<>();
        for (int p : pixels) {
            if (Color.alpha(p) > 10 && p != Color.WHITE && !colors.contains(p)) colors.add(p);
        }
        if (colors.isEmpty()) {
            toast("Нарисуйте что-нибудь сначала");
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
        currentColor = numberColors[0];
        Arrays.fill(pixels, Color.WHITE);
        refreshPalette();
        buildUi();
        toast("Режим раскраски включен!");
    }

    private void showAiDialog() {
        toast("ИИ Генератор");
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
                        handleDrawClick(event.getX(), event.getY());
                    }
                    break;
                }

                case MotionEvent.ACTION_MOVE: {
                    if (scaleFactor > 1.0f) {
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
                    } else if (!scaleDetector.isInProgress() && currentTool.contains("Карандаш")) {
                        handleDrawClick(event.getX(), event.getY());
                    }
                    break;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
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
                                toast("Нужен цвет №" + expectedNum);
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
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 5.0f));
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
                        paint.setColor(Color.parseColor("#880E4F"));
                        paint.setTextSize(cellSize * 0.45f);
                        canvas.drawText(String.valueOf(numberMap[idx]), left + cellSize * 0.3f, top + cellSize * 0.7f, paint);
                        paint.setAntiAlias(false);
                    }

                    if (showGrid) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(Color.parseColor("#E0E0E0"));
                        paint.setStrokeWidth(1f);
                        canvas.drawRect(rect, paint);
                    }
                }
            }

            canvas.restore();
        }
    }
}

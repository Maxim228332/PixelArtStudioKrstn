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
    private Bitmap referenceBitmap;
    private boolean showReference = true;
    private boolean showGrid = true;
    private int currentColor = Color.parseColor("#E91E63");
    private String currentTool = "✏️ Карандаш";
    
    // Новые фичи
    private boolean freeDrawMode = false; // Свободное рисование (не по пикселям)
    private boolean symmetryMode = false; // Режим зеркала
    private Bitmap freeDrawBitmap;
    private Canvas freeDrawCanvas;
    private Path currentPath = new Path();
    private float freeX, freeY;

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
        hideSystemUI();
        seedPalette();
        Arrays.fill(pixels, Color.WHITE);
        buildUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUi

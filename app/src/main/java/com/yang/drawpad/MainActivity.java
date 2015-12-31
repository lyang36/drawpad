package com.yang.drawpad;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    int maxBrushSize = 100;
    int initialBrushSize = 1;
    int brushSize = 1;
    int opacity = 255;
    int currentColor = Color.parseColor("#000000");


    private SurfaceCanvasView canvasView;

    private View undoButton;
    private ColorDotView colorPickerButton;
    private RangePickBar brushPickingBar;
    private RangePickBar opacityPickingBar;
    private View colorPickerView;

    private ColorDotView sampleDotView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        canvasView = (SurfaceCanvasView) findViewById(R.id.canvasView);
        sampleDotView = (ColorDotView) findViewById(R.id.colorDot);
        colorPickerButton = (ColorDotView) findViewById(R.id.colorPicker_button);
        colorPickerView = findViewById(R.id.colorPicker);

        sampleDotView.setRingRadius(0);
        sampleDotView.setRingRadius(0);
        sampleDotView.setAlpha(opacity);
        sampleDotView.setFaceColor(currentColor);
        colorPickerButton.setFaceColor(currentColor);

        canvasView.setPaintStrokeWidth(brushSize);


        undoButton = findViewById(R.id.imageView_undo);

        undoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(canvasView != null){
                    canvasView.undo();
                }
            }
        });

        brushPickingBar = new RangePickBar(this,
                findViewById(R.id.adjustingDot_brush),
                findViewById(R.id.adjustingBar_brush),
                findViewById(R.id.drawpad_sample_dot_view));

        brushPickingBar.setOnRangePickListener(new RangePickBar.OnRangePickedListener() {
            @Override
            public void onRangePicked(float range) {
                brushSize = (int)(range * maxBrushSize + initialBrushSize);
                setSampleDotView();
                canvasView.setPaintStrokeWidth(brushSize);
            }
        });

        opacityPickingBar = new RangePickBar(this,
                findViewById(R.id.adjustingDot_opacity),
                findViewById(R.id.adjustingBar_opacity),
                findViewById(R.id.drawpad_sample_dot_view));
        opacityPickingBar.setCurrentY(opacityPickingBar.getMaxY());
        opacityPickingBar.setOnRangePickListener(new RangePickBar.OnRangePickedListener() {
            @Override
            public void onRangePicked(float range) {
                opacity = (int) (range * 255);
                canvasView.setOpacity(opacity);
                setSampleDotView();
            }
        });

        colorPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                colorPickerView.setVisibility(View.VISIBLE);
            }
        });

        String[] colors = new String[]{"#ff0000", "#00ff00", "#0000ff", "#000000", "#ffffff"};

        final LinearLayout colorPickerContainer = (LinearLayout) findViewById(R.id.colorPicker_container);
        for(int i= 0; i < colors.length; i++){
            View view = View.inflate(this, R.layout.list_item_color_dots, null);
            final ColorDotView colorDotView = (ColorDotView) view.findViewById(R.id.colorDot);
            colorDotView.setFaceColor(Color.parseColor(colors[i]));
            colorPickerContainer.addView(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentColor = colorDotView.getFaceColor();
                    colorPickerButton.setFaceColor(currentColor);
                    canvasView.setPaintStrokeColor(currentColor);
                    colorPickerView.setVisibility(View.GONE);
                }
            });
        }
        colorPickerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                colorPickerView.setVisibility(View.GONE);
                return false;
            }
        });
    }

    void setSampleDotView(){
        ViewGroup.LayoutParams params
                = sampleDotView.getLayoutParams();
        if(params != null){

            params.height = brushSize;
            params.width = params.height;
            sampleDotView.setLayoutParams(params);
        }
        sampleDotView.setAlpha(opacity);
        sampleDotView.setFaceColor(currentColor);
    }

    @Override
    protected void onPause() {
        super.onPause();
        canvasView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        canvasView.resume();
    }

    public class GridElementAdapter extends RecyclerView.Adapter<GridElementAdapter.SimpleViewHolder>{

        private Context context;
        private List<String> colorStrings;

        public GridElementAdapter(Context context){
            this.context = context;
            this.colorStrings = new ArrayList<String>();
        }

        @Override
        public SimpleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(this.context).inflate(R.layout.subview_color_pick_dots, parent, false);
            return new SimpleViewHolder(view);
        }

        @Override
        public void onBindViewHolder(SimpleViewHolder holder, final int position) {
            holder.colorDotView.setFaceColor(Color.parseColor(colorStrings.get(position)));
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return this.colorStrings.size();
        }

        class SimpleViewHolder extends RecyclerView.ViewHolder {
            public final ColorDotView colorDotView;

            public SimpleViewHolder(View view) {
                super(view);
                colorDotView = (ColorDotView) view.findViewById(R.id.colorDot);
            }
        }
    }

}

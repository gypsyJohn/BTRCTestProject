package ie.gypsy.btrctestproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class TractorLocatorView extends View {
    Integer heightOfScreen, widthOfScreen;
    Integer lengthOfBox, widthOfBox;
    Integer tractorX, tractorY;
    int scaledTractorX, scaledTractorY;
    int scaledLengthOfBox, scaledWidthOfBox;
    int centreHorizontal, centreVertical;
    Paint paint;
    int pixPerCm = 1;


    public TractorLocatorView(Context context) {
        super(context);
        paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(6f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(lengthOfBox != null){drawBoxOutline(canvas);}
        if(tractorX != null){drawTractorDot(canvas);}

    }

    void drawBoxOutline(Canvas canvas){
        canvas.drawLine(centreHorizontal - scaledWidthOfBox, centreVertical - scaledLengthOfBox, centreHorizontal - scaledWidthOfBox, centreVertical + scaledLengthOfBox,paint);
        canvas.drawLine(centreHorizontal + scaledWidthOfBox, centreVertical - scaledLengthOfBox, centreHorizontal + scaledWidthOfBox, centreVertical + scaledLengthOfBox,paint);
        canvas.drawLine(centreHorizontal - scaledWidthOfBox, centreVertical - scaledLengthOfBox, centreHorizontal + scaledWidthOfBox, centreVertical - scaledLengthOfBox,paint);
        canvas.drawLine(centreHorizontal - scaledWidthOfBox, centreVertical + scaledLengthOfBox, centreHorizontal + scaledWidthOfBox, centreVertical + scaledLengthOfBox,paint);
    }

    void drawTractorDot(Canvas canvas){
        canvas.drawCircle(scaledTractorX, scaledTractorY, 10,paint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        heightOfScreen = h;
        widthOfScreen = w;
        centreVertical = heightOfScreen/2;
        centreHorizontal = widthOfScreen/2;
    }

    void setTractorPosition(int[] tractorPosition){
        if(lengthOfBox == null){
            lengthOfBox = tractorPosition[1];
            widthOfBox = tractorPosition[0];
            pixPerCm = Math.min((heightOfScreen-100)/lengthOfBox,(widthOfScreen-100)/widthOfBox);
            scaledLengthOfBox = pixPerCm * lengthOfBox;
            scaledWidthOfBox = pixPerCm * widthOfScreen;
        }else{
            tractorX = tractorPosition[0];
            tractorY = tractorPosition[1];
            scaledTractorX = pixPerCm * tractorX;
            scaledTractorY = pixPerCm * tractorY;
            invalidate();
        }
    }
}

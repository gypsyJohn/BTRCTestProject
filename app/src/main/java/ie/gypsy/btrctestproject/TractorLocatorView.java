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
    int usleft, usfront, usright, usback;


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
        if(tractorX != null){drawTractorDot(canvas);drawPositions(canvas);}

    }

    void drawBoxOutline(Canvas canvas){
        canvas.drawLine(0, 0, 0, scaledLengthOfBox,paint);
        canvas.drawLine(scaledWidthOfBox, 0, scaledWidthOfBox, scaledLengthOfBox,paint);
        canvas.drawLine(0, 0, scaledWidthOfBox, 0,paint);
        canvas.drawLine(0, scaledLengthOfBox, scaledWidthOfBox, scaledLengthOfBox,paint);
    }

    void drawTractorDot(Canvas canvas){
        canvas.drawCircle(scaledTractorX, scaledTractorY, 10,paint);
    }

    void drawPositions(Canvas canvas){
        canvas.drawText("Left = " + usleft,700,100,paint);
        canvas.drawText("Right = " + usright,700,200,paint);
        canvas.drawText("Front = " + usfront,700,300,paint);
        canvas.drawText("Back = " + usback,700,400,paint);
        canvas.drawText("Ty = " + tractorX,700,500,paint);
        canvas.drawText("Tx = " + tractorY,700,600,paint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        heightOfScreen = h;
        widthOfScreen = w;
        centreVertical = heightOfScreen/2;
        centreHorizontal = widthOfScreen/2;
    }

    void setTractorPosition(RCTractorControlClass.TractorPosnMsg tractorPosnMsg){
        if(lengthOfBox == null){
            lengthOfBox = tractorPosnMsg.positions[1];
            widthOfBox = tractorPosnMsg.positions[0];
            pixPerCm = Math.min((heightOfScreen-100)/lengthOfBox,(widthOfScreen-100)/widthOfBox);
            scaledLengthOfBox = pixPerCm * lengthOfBox;
            scaledWidthOfBox = pixPerCm * widthOfScreen;
        }else{
            tractorX = tractorPosnMsg.positions[0];
            tractorY = tractorPosnMsg.positions[1];
            scaledTractorX = pixPerCm * tractorX;
            scaledTractorY = pixPerCm * tractorY;
            usleft = tractorPosnMsg.left;
            usback = tractorPosnMsg.back;
            usright = tractorPosnMsg.right;
            usfront = tractorPosnMsg.front;
            invalidate();
        }
    }
}

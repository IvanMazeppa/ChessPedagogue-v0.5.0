package com.example.chesspedagogue;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.content.ContextCompat;
import java.util.*;

/** Custom View that draws the chessboard and animates moves. */
public class ChessBoardView extends View {

    /* ───── paints / shaders ───── */
    private Paint lightPaint, darkPaint, selectedPaint, legalMovePaint;
    private BitmapShader lightShader, darkShader;

    /* ───── board state ───── */
    private int squareSize;
    private boolean flipped = false;
    private int selectedRow = -1, selectedCol = -1;
    private final char[][] boardState = {
            {'r','n','b','q','k','b','n','r'},
            {'p','p','p','p','p','p','p','p'},
            {' ',' ',' ',' ',' ',' ',' ',' '},
            {' ',' ',' ',' ',' ',' ',' ',' '},
            {' ',' ',' ',' ',' ',' ',' ',' '},
            {' ',' ',' ',' ',' ',' ',' ',' '},
            {'P','P','P','P','P','P','P','P'},
            {'R','N','B','Q','K','B','N','R'}
    };

    /* ───── legal‑move highlights ───── */
    private final List<int[]> highlightSquares = new ArrayList<>();
    private ValueAnimator legalAnimator;
    private int legalAlpha = 0;

    /* ───── moving‑piece sprite ───── */
    private static class MovingPiece {
        final Drawable d;
        final float sx, sy, ex, ey;
        final int toRow, toCol;
        final long startT, dur = 200;   // ms
        MovingPiece(Drawable d, float sx, float sy, float ex, float ey,
                    int toRow, int toCol) {
            this.d = d; this.sx = sx; this.sy = sy;
            this.ex = ex; this.ey = ey;
            this.toRow = toRow; this.toCol = toCol;
            startT = System.currentTimeMillis();
        }
        /** Draws at interpolated position; returns true when finished. */
        boolean draw(Canvas c, int sq, int pad) {
            float t = Math.min(1f, (System.currentTimeMillis()-startT)/ (float) dur);
            float x = sx + (ex - sx) * t, y = sy + (ey - sy) * t;
            d.setBounds(Math.round(x)+pad, Math.round(y)+pad,
                    Math.round(x)+sq-pad, Math.round(y)+sq-pad);
            d.draw(c);
            return t == 1f;
        }
    }
    private final List<MovingPiece> movingPieces = new ArrayList<>();

    /* ───── tap listener ───── */
    public interface OnSquareTapListener { void onSquareTapped(int row,int col); }
    private OnSquareTapListener squareTapListener;

    /* ───── ctor ───── */
    public ChessBoardView(Context c){super(c);init();}
    public ChessBoardView(Context c,AttributeSet a){super(c,a);init();}

    /* ───────── init ───────── */
    private void init() {
        // wood texture
        Bitmap raw = BitmapFactory.decodeResource(getResources(), R.drawable.wood_board);
        int tgt = Math.min(1024, getResources().getDisplayMetrics().widthPixels);
        Bitmap base = Bitmap.createScaledBitmap(raw, tgt, tgt, true);

        lightShader = new BitmapShader(base, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Bitmap darkBmp = base.copy(base.getConfig(), true);
        new Canvas(darkBmp).drawColor(0x61000000, PorterDuff.Mode.SRC_ATOP);
        darkShader = new BitmapShader(darkBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

        lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG); lightPaint.setShader(lightShader);
        darkPaint  = new Paint(Paint.ANTI_ALIAS_FLAG); darkPaint.setShader(darkShader);

        float dp = getResources().getDisplayMetrics().density;
        selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setStrokeWidth(dp*4);
        selectedPaint.setColor(0xFFFFC107);
        selectedPaint.setShadowLayer(dp*6,0,0,0x66FFC107);
        setLayerType(LAYER_TYPE_HARDWARE, selectedPaint);

        legalMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legalMovePaint.setColor(0x660000FF);
    }

    /* ─────────   DRAW   ───────── */
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        squareSize = getWidth()/8;
        int pad = squareSize/16;

        /* 1) squares */
        for(int r=0;r<8;r++) for(int c=0;c<8;c++){
            int br = flipped?7-r:r, bc = flipped?7-c:c;
            Paint p=((br+bc)&1)==0?lightPaint:darkPaint;
            float l=c*squareSize-0.5f,t=r*squareSize-0.5f;
            canvas.drawRect(l,t,l+squareSize+1,t+squareSize+1,p);
        }

        /* 2) legal‑move dots */
        for(int[] s:highlightSquares){
            int dr=flipped?7-s[0]:s[0], dc=flipped?7-s[1]:s[1];
            canvas.drawCircle(dc*squareSize+squareSize/2f,
                    dr*squareSize+squareSize/2f,
                    squareSize/8f,legalMovePaint);
        }

        /* 3) static pieces (skip squares with active sprite) */
        for(int r=0;r<8;r++) for(int c=0;c<8;c++){
            boolean covered=false;
            for(MovingPiece mp:movingPieces){
                if(mp.toRow==r && mp.toCol==c){ covered=true; break; }
            }
            if(covered) continue;

            char pc=boardState[r][c];
            if(pc==' ') continue;
            int vr=flipped?7-r:r, vc=flipped?7-c:c;
            Drawable d=ContextCompat.getDrawable(getContext(),getDrawableForPiece(pc));
            if(d==null) continue;
            d.setBounds(vc*squareSize+pad,vr*squareSize+pad,
                    vc*squareSize+squareSize-pad,vr*squareSize+squareSize-pad);
            d.draw(canvas);
        }

        /* 4) glow */
        if(selectedRow!=-1){
            int l=selectedCol*squareSize,t=selectedRow*squareSize;
            canvas.drawRoundRect(l,t,l+squareSize,t+squareSize,
                    squareSize*0.1f,squareSize*0.1f,selectedPaint);
        }

        /* 5) moving sprite */
        if(!movingPieces.isEmpty()){
            Iterator<MovingPiece> it=movingPieces.iterator();
            while(it.hasNext()) if(it.next().draw(canvas,squareSize,pad)) it.remove();
            if(!movingPieces.isEmpty()) postInvalidateOnAnimation();
        }
    }

    /* ───────── external API ───────── */
    public int getSelectedRow(){return selectedRow==-1?-1:(flipped?7-selectedRow:selectedRow);}
    public int getSelectedCol(){return selectedCol==-1?-1:(flipped?7-selectedCol:selectedCol);}
    public char getPieceAt(int r,int c){return boardState[r][c];}

    public void updateBoardFromFen(String fen) {
        if (fen == null || fen.isEmpty()) return;

        // Create a temporary copy of the current board state
        char[][] newBoardState = new char[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(boardState[i], 0, newBoardState[i], 0, 8);
        }

        // Split off just the square‑placement part
        String[] parts = fen.split("\\s+");
        String[] ranks = parts[0].split("/");

        // For each of the 8 ranks...
        for (int r = 0; r < 8; r++) {
            int c = 0;
            for (char ch : ranks[r].toCharArray()) {
                if (Character.isDigit(ch)) {
                    // e.g. '3' means three empty squares
                    int empty = ch - '0';
                    for (int i = 0; i < empty; i++) {
                        newBoardState[r][c++] = ' ';
                    }
                } else {
                    // a letter is a piece
                    newBoardState[r][c++] = ch;
                }
            }
        }

        // Check if anything actually changed
        boolean boardChanged = false;
        for (int r = 0; r < 8 && !boardChanged; r++) {
            for (int c = 0; c < 8; c++) {
                if (boardState[r][c] != newBoardState[r][c]) {
                    boardChanged = true;
                    break;
                }
            }
        }

        // Only update if something changed
        if (boardChanged) {
            // Update the board state
            for (int r = 0; r < 8; r++) {
                System.arraycopy(newBoardState[r], 0, boardState[r], 0, 8);
            }

            // Clear any selection/highlights now that the board has re‑drawn
            selectedRow = selectedCol = -1;
            highlightSquares.clear();

            // Finally, redraw the view
            invalidate();
        }
    }



    public void setFlipped(boolean f){flipped=f;invalidate();}
    public void setSelectedSquare(int br,int bc){
        selectedRow=flipped?7-br:br; selectedCol=flipped?7-bc:bc; invalidate();
    }
    public void clearSelectionHighlight(){selectedRow=selectedCol=-1;invalidate();}

    public void addHighlightedSquare(int r,int c){
        highlightSquares.add(new int[]{r,c});
        if(highlightSquares.size()==1) startLegalMoveAnimation();
    }
    public void clearHighlightedSquares(){
        highlightSquares.clear();legalMovePaint.setAlpha(0);legalAlpha=0;invalidate();
    }

    /** Call after updateBoardFromFen to slide a piece. */
    public void animateMove(int fromR, int fromC, int toR, int toC) {
        int vfr = flipped ? 7-fromR : fromR,   vfc = flipped ? 7-fromC : fromC;
        int vtr = flipped ? 7-toR   : toR,     vtc = flipped ? 7-toC   : toC;

        char pc = boardState[toR][toC];
        int  res = getDrawableForPiece(pc);
        if (res == 0) {          // <‑‑ safe‑guard: nothing to draw, just return
            return;
        }

        Drawable d = ContextCompat.getDrawable(getContext(), res);
        if (d == null) return;   // (extra guard)

        movingPieces.add(new MovingPiece(
                d,
                vfc * squareSize, vfr * squareSize,
                vtc * squareSize, vtr * squareSize,
                toR, toC));
        postInvalidateOnAnimation();
    }


    /* touch handling */
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_DOWN) return super.onTouchEvent(e);
        int vc=(int)(e.getX()/squareSize), vr=(int)(e.getY()/squareSize);
        if(vr<0||vr>7||vc<0||vc>7) return true;
        int br=flipped?7-vr:vr, bc=flipped?7-vc:vc;
        if(squareTapListener!=null) squareTapListener.onSquareTapped(br,bc);
        return true;
    }
    public void setOnSquareTapListener(OnSquareTapListener l){squareTapListener=l;}

    /* helpers */
    private void startLegalMoveAnimation(){
        if(legalAnimator!=null&&legalAnimator.isRunning()) legalAnimator.cancel();
        legalAnimator=ValueAnimator.ofInt(0,255);
        legalAnimator.setDuration(200);
        legalAnimator.addUpdateListener(a->{
            legalAlpha=(int)a.getAnimatedValue();
            legalMovePaint.setAlpha(legalAlpha); invalidate();});
        legalAnimator.start();
    }

    private int getDrawableForPiece(char p){
        switch(p){
            case 'P':return R.drawable.ic_white_pawn;
            case 'R':return R.drawable.ic_white_rook;
            case 'N':return R.drawable.ic_white_knight;
            case 'B':return R.drawable.ic_white_bishop;
            case 'Q':return R.drawable.ic_white_queen;
            case 'K':return R.drawable.ic_white_king;
            case 'p':return R.drawable.ic_black_pawn;
            case 'r':return R.drawable.ic_black_rook;
            case 'n':return R.drawable.ic_black_knight;
            case 'b':return R.drawable.ic_black_bishop;
            case 'q':return R.drawable.ic_black_queen;
            case 'k':return R.drawable.ic_black_king;
            default: return 0;
        }
    }
}

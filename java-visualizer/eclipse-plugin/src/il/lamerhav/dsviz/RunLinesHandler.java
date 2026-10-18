package il.lamerhav.dsviz;

/** "הרצה שורה אחר שורה": כל שורה בקוד היא צעד בחלון, עם המשתנים המקומיים */
public class RunLinesHandler extends RunVisualHandler {
    @Override
    protected boolean lineByLine() {
        return true;
    }
}

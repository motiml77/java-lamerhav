package il.lamerhav.dsviz;

/** מדפיס את מזהה המחשב ואת המזהה הקבוע (לבדיקה station-id.ps1, ב-Windows אמיתי) */
public class StationId {
    public static void main(String[] a) {
        System.out.println("MACHINE " + Account.machine());
        System.out.println("STABLE " + Account.stableMachine());
    }
}

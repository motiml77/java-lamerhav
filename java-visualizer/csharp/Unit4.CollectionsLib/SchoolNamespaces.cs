// ה-namespaces של הספריות הגרפיות ב-Unit4.dll של בתי הספר — ריקים, רק כדי ש-using אליהם ייבנה גם בפרויקט שמקבל את הספרייה של DSViz.
//
// בתבנית של בתי הספר יש בראש הקובץ using Unit4.GridLib; (ולפעמים TurtleLib, FrogLib...) גם כשלא משתמשים בהן. בפרויקט שמפנה ל-Unit4.dll
// של בית הספר זה נבנה. אבל בפרויקט בלי Unit4.dll — למשל פרויקט חדש ש-DSViz.targets נותן לו את Unit4.CollectionsLib, ומדביקים בו את הקוד
// מהתבנית — ההרצה הרגילה (הכפתור הירוק) נכשלה ב-CS0234 ("The type or namespace name 'GridLib' does not exist in the namespace 'Unit4'"),
// כאילו DSViz שבר את הקוד. כאן כל namespace כזה קיים, עם מחלקה פנימית אחת (internal — לא נראית מבחוץ, אין לה API ואין התנגשות עם שום
// ספרייה). קוד שבאמת משתמש בסוג גרפי (new Grid()) — עדיין שגיאה ברורה (CS0246), וההרצה הוויזואלית מסבירה בעברית (SchoolUsings ב-dsviz-cs).
// הרשימה — כל ה-namespaces הגרפיים בשתי הגרסאות האמיתיות של Unit4.dll (של המפקחים ושל המורים); נבדק ב-cs-tests מול שתיהן.

namespace Unit4.GridLib { internal static class DsVizSchoolNamespace { } }
namespace Unit4.TurtleLib { internal static class DsVizSchoolNamespace { } }
namespace Unit4.FrogLib { internal static class DsVizSchoolNamespace { } }
namespace Unit4.TowerLib { internal static class DsVizSchoolNamespace { } }
namespace Unit4.HanoiLib { internal static class DsVizSchoolNamespace { } }
namespace Unit4.CardLib { internal static class DsVizSchoolNamespace { } }
namespace Unit4.PolygonLib { internal static class DsVizSchoolNamespace { } }
namespace Unit4.OrganLib { internal static class DsVizSchoolNamespace { } }
namespace Unit4.DiceLib { internal static class DsVizSchoolNamespace { } }
namespace Unit4.DreidelLib { internal static class DsVizSchoolNamespace { } }
namespace Unit4.BucketLib { internal static class DsVizSchoolNamespace { } }

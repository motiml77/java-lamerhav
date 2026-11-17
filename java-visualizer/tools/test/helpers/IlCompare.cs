// IlCompare — משווה את קוד ה-IL של כל פעולה בסוגים של האוספים / BinTreeUtils / TreeCanvas בין שתי גרסאות של Unit4.dll — אחרי פענוח ה-tokens
// לשמות (סוג::פעולה, מחרוזת), כך שמספור שונה של ה-tokens בין הקבצים לא נחשב הבדל
using System.Collections.Immutable;
using System.Reflection;
using System.Reflection.Metadata;
using System.Reflection.Metadata.Ecma335;
using System.Reflection.PortableExecutable;
//   IlCompare <Unit4.dll א> <Unit4.dll ב> [חלק משם — מדפיס את שתי הגרסאות של פעולה שונה]
// מדפיס DIFF לכל פעולה שונה / שקיימת רק באחד, ובסוף same=… diff=…. משמש את cs-tests (סעיף ל) כשיש את גרסת המורים במחשב.
using System.Linq;

static class IlCompare
{
    static Dictionary<string, string> Bodies(string file, string[] nss) {
      var fs = File.OpenRead(file); var pe = new PEReader(fs); var md = pe.GetMetadataReader();
      var res = new Dictionary<string, string>();
      var P = new Prov(md);
      string MSig(BlobHandle b) { try { var s = new BlobReader[] { md.GetBlobReader(b) }; var dec = new SignatureDecoder<string, object>(P, md, null); var r = s[0]; var hdr = r.ReadSignatureHeader(); r.Reset(); if (hdr.Kind == SignatureKind.Field) return dec.DecodeFieldSignature(ref r); var ms = dec.DecodeMethodSignature(ref r); return ms.ReturnType + "(" + string.Join(",", ms.ParameterTypes) + ")"; } catch (Exception e) { return "?" + e.Message; } }
      string Tok(int tok) {
        var h = MetadataTokens.EntityHandle(tok);
        try {
          switch (h.Kind) {
            case HandleKind.MethodDefinition: { var m = md.GetMethodDefinition((MethodDefinitionHandle)h); var t = md.GetTypeDefinition(m.GetDeclaringType()); return md.GetString(t.Namespace) + "." + md.GetString(t.Name) + "::" + md.GetString(m.Name) + MSig(m.Signature); }
            case HandleKind.MemberReference: { var m = md.GetMemberReference((MemberReferenceHandle)h); string p = m.Parent.Kind == HandleKind.TypeReference ? md.GetString(md.GetTypeReference((TypeReferenceHandle)m.Parent).Namespace) + "." + md.GetString(md.GetTypeReference((TypeReferenceHandle)m.Parent).Name) : m.Parent.Kind == HandleKind.TypeSpecification ? md.GetTypeSpecification((TypeSpecificationHandle)m.Parent).DecodeSignature(P, null) : m.Parent.Kind == HandleKind.TypeDefinition ? md.GetString(md.GetTypeDefinition((TypeDefinitionHandle)m.Parent).Name) : m.Parent.Kind.ToString(); return p + "::" + md.GetString(m.Name) + MSig(m.Signature); }
            case HandleKind.FieldDefinition: { var f = md.GetFieldDefinition((FieldDefinitionHandle)h); return "field " + md.GetString(f.Name) + ":" + MSig(f.Signature); }
            case HandleKind.TypeDefinition: { var t = md.GetTypeDefinition((TypeDefinitionHandle)h); return md.GetString(t.Namespace) + "." + md.GetString(t.Name); }
            case HandleKind.TypeReference: { var t = md.GetTypeReference((TypeReferenceHandle)h); return md.GetString(t.Namespace) + "." + md.GetString(t.Name); }
            case HandleKind.TypeSpecification: return md.GetTypeSpecification((TypeSpecificationHandle)h).DecodeSignature(P, null);
            case HandleKind.MethodSpecification: { var s = md.GetMethodSpecification((MethodSpecificationHandle)h); return "mspec(" + Tok(MetadataTokens.GetToken(s.Method)) + "<" + string.Join(",", s.DecodeSignature(P, null)) + ">)"; }
            case HandleKind.StandaloneSignature: return "sig";
          }
        } catch { }
        return h.Kind.ToString();
      }
      foreach (var th in md.TypeDefinitions) {
        var t = md.GetTypeDefinition(th); string ns = md.GetString(t.Namespace);
        if (!nss.Contains(ns)) continue;
        foreach (var mh in t.GetMethods()) {
          var m = md.GetMethodDefinition(mh);
          if (m.RelativeVirtualAddress == 0) continue;
          var body = pe.GetMethodBody(m.RelativeVirtualAddress);
          var il = body.GetILBytes();
          // פענוח פשוט של ה-IL: כל token (4 בתים אחרי opcode שמקבל token) מוחלף בשם
          var sb = new System.Text.StringBuilder();
          var r = body.GetILReader();
          while (r.RemainingBytes > 0) {
            int op = r.ReadByte(); if (op == 0xFE) op = 0xFE00 | r.ReadByte();
            sb.Append(op.ToString("X")).Append(' ');
            switch (op) {
              case 0x28: case 0x6F: case 0x73: case 0x7B: case 0x7D: case 0x7C: case 0x7E: case 0x80: case 0x8C: case 0x79: case 0x74: case 0x75: case 0xA5: case 0x8D: case 0xD0: case 0xFE06: case 0xFE07: case 0x27: case 0x8F: case 0xA3: case 0xA4: case 0xC2: case 0xC6: case 0x70: case 0x81: case 0x71: case 0xFE15: case 0xFE16: case 0xFE1C: case 0x29:
                { int tok = r.ReadInt32(); if (op == 0x72) break; sb.Append('[').Append(Tok(tok)).Append("] "); break; }
              case 0x72: { int tok = r.ReadInt32(); sb.Append('"').Append(md.GetUserString(MetadataTokens.UserStringHandle(tok & 0xFFFFFF))).Append("\" "); break; }
              case 0x20: case 0x22: case 0x38: case 0x39: case 0x3A: case 0x3B: case 0x3C: case 0x3D: case 0x3E: case 0x3F: case 0x40: case 0x41: case 0x42: case 0x43: case 0x44: case 0xDD: sb.Append(r.ReadInt32()).Append(' '); break;
              case 0x21: case 0x23: sb.Append(r.ReadInt64()).Append(' '); break;
              case 0x1F: case 0x2B: case 0x2C: case 0x2D: case 0x2E: case 0x2F: case 0x30: case 0x31: case 0x32: case 0x33: case 0x34: case 0x35: case 0x36: case 0x37: case 0xDE: case 0x0E: case 0x0F: case 0x10: case 0x11: case 0x12: case 0x13: sb.Append(r.ReadByte()).Append(' '); break;
              case 0xFE09: case 0xFE0A: case 0xFE0B: case 0xFE0C: case 0xFE0D: case 0xFE0E: sb.Append(r.ReadUInt16()).Append(' '); break;
              case 0x45: { int n = r.ReadInt32(); for (int i = 0; i < n; i++) sb.Append(r.ReadInt32()).Append(','); break; }
            }
          }
          var locals = body.LocalSignature.IsNil ? "" : string.Join(",", md.GetStandaloneSignature(body.LocalSignature).DecodeLocalSignature(P, null));
          res[ns + "." + md.GetString(t.Name) + "::" + md.GetString(m.Name) + MSig(m.Signature)] = "locals(" + locals + ") maxstack " + body.MaxStack + " :: " + sb.ToString();
        }
      }
      return res;
    }

    static int Main(string[] args)
    {
        var nss = new[] { "Unit4.CollectionsLib", "Unit4.BinTreeUtilsLib", "Unit4.BinTreeCanvasLib" };
        var a = Bodies(args[0], nss); var b = Bodies(args[1], nss);
        int same = 0, diff = 0;
        foreach (var k in a.Keys.Union(b.Keys).OrderBy(x => x)) {
          a.TryGetValue(k, out var x); b.TryGetValue(k, out var y);
          if (x == y) same++; else { diff++; Console.WriteLine("DIFF " + k + (x == null ? " (only in 2)" : y == null ? " (only in 1)" : "")); if (args.Length > 2 && k.Contains(args[2]) && x != null && y != null) { Console.WriteLine("  1: " + x); Console.WriteLine("  2: " + y); } }
        }
        Console.WriteLine($"same={same} diff={diff}");
        return 0;
    }
}

class Prov : ISignatureTypeProvider<string, object> {
  MetadataReader md; public Prov(MetadataReader m) { md = m; }
  public string GetArrayType(string e, ArrayShape s) => e + "[,]";
  public string GetByReferenceType(string e) => "ref " + e;
  public string GetFunctionPointerType(MethodSignature<string> s) => "fnptr";
  public string GetGenericInstantiation(string g, ImmutableArray<string> a) => g + "<" + string.Join(",", a) + ">";
  public string GetGenericMethodParameter(object c, int i) => "!!" + i;
  public string GetGenericTypeParameter(object c, int i) => "!" + i;
  public string GetModifiedType(string m, string u, bool r) => u;
  public string GetPinnedType(string e) => e;
  public string GetPointerType(string e) => e + "*";
  public string GetPrimitiveType(PrimitiveTypeCode t) => t.ToString();
  public string GetSZArrayType(string e) => e + "[]";
  public string GetTypeFromDefinition(MetadataReader r, TypeDefinitionHandle h, byte k) { var t = r.GetTypeDefinition(h); return r.GetString(t.Namespace) + "." + r.GetString(t.Name); }
  public string GetTypeFromReference(MetadataReader r, TypeReferenceHandle h, byte k) { var t = r.GetTypeReference(h); return r.GetString(t.Namespace) + "." + r.GetString(t.Name); }
  public string GetTypeFromSpecification(MetadataReader r, object c, TypeSpecificationHandle h, byte k) => r.GetTypeSpecification(h).DecodeSignature(this, c);
}

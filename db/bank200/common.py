"""Deterministic question authoring. Expected answers come from Python oracles."""
import random

BANK = []

def text(value):
    if isinstance(value, bool):
        return 'true' if value else 'false'
    if isinstance(value, (list, tuple)):
        return ' '.join(map(str, value)) if value else 'EMPTY'
    return str(value)

def add(slug, title, difficulty, task, layout, cases, encode, oracle, java):
    assert len(cases) == 10, (slug, len(cases))
    tests = [(encode(c), text(oracle(c))) for c in cases]
    assert len({i for i, _ in tests}) == 10, slug
    assert all(i.isascii() and o.isascii() and o.strip() for i, o in tests)
    prompt = (task + '\n\nInput\n' + layout + '\n\n'
              'Read standard input and print only the requested result. Array indices are zero-based. '
              'Use 64-bit integers for sums and counts unless stated otherwise.\n\n')
    for n, (i, o) in enumerate(tests[:2], 1):
        prompt += f'Example {n}\nInput:\n{i.rstrip()}\nOutput:\n{o}\n\n'
    BANK.append(dict(slug=slug, title=title, difficulty=difficulty, prompt=prompt.strip(),
                     body=java, tests=tests))

def arr_input(c):
    return f'{len(c)}\n' + ' '.join(map(str, c)) + '\n'

def arrk_input(c):
    a, k = c
    return f'{len(a)} {k}\n' + ' '.join(map(str, a)) + '\n'

def words_input(c):
    return '\n'.join(c) + '\n' if isinstance(c, tuple) else c + '\n'

def numbers_input(c):
    return text(c) + '\n'

def arrays(seed=1, nonnegative=False):
    r = random.Random(seed)
    base = [[3, 1, 4, 1, 5], [2, 2, 2], [0], [7], [0, 0, 0, 0],
            list(range(12)), list(range(15, 0, -1)), [9, 0, 9, 0, 3, 3],
            [r.randint(0, 30) for _ in range(47)], [r.randint(0, 1000) for _ in range(160)]]
    if not nonnegative:
        base[6] = list(range(6, -9, -1))
        base[7] = [-5, 4, -2, 4, -5, 9]
        base[8] = [x-15 for x in base[8]]
        base[9] = [x-500 for x in base[9]]
    return base

ARRAY_LAYOUT = 'An integer n (1 <= n <= 2000), then n integers a[i] (-1000000 <= a[i] <= 1000000).'
POS_ARRAY_LAYOUT = 'An integer n (1 <= n <= 2000), then n integers a[i] (0 <= a[i] <= 1000000).'

JAVA_SUPPORT = r'''
import java.io.*;
import java.util.*;
public class Main {
  static final long MOD = 1000000007L;
  static class FastScanner {
    private final InputStream in = System.in;
    private final byte[] buffer = new byte[65536];
    private int pos, len;
    int read() throws IOException {
      if(pos >= len) { len=in.read(buffer); pos=0; if(len<0) return -1; }
      return buffer[pos++];
    }
    String next() throws IOException {
      StringBuilder b=new StringBuilder(); int c;
      do { c=read(); } while(c<=32 && c!=-1);
      if(c==-1) throw new EOFException();
      while(c>32 && c!=-1) { b.append((char)c); c=read(); }
      return b.toString();
    }
    int nextInt() throws IOException { return Integer.parseInt(next()); }
    long nextLong() throws IOException { return Long.parseLong(next()); }
  }
  static int[] array(FastScanner in, int n) throws Exception {
    int[] a=new int[n]; for(int i=0;i<n;i++) a[i]=in.nextInt(); return a;
  }
  static String join(int[] a) {
    if(a.length==0) return "EMPTY";
    StringJoiner j=new StringJoiner(" "); for(int x:a) j.add(""+x); return j.toString();
  }
  static String join(long[] a) {
    if(a.length==0) return "EMPTY";
    StringJoiner j=new StringJoiner(" "); for(long x:a) j.add(""+x); return j.toString();
  }
  static String join(Collection<?> a) {
    if(a.isEmpty()) return "EMPTY";
    StringJoiner j=new StringJoiner(" "); for(Object x:a) j.add(""+x); return j.toString();
  }
  static long gcd(long a,long b) { while(b!=0){long t=a%b;a=b;b=t;} return Math.abs(a); }
  static long power(long a,long b,long m) {long r=1%m;while(b>0){if((b&1)==1)r=r*a%m;a=a*a%m;b>>=1;}return r;}
  static String solve(FastScanner in) throws Exception {
%BODY%
  }
  public static void main(String[] args) throws Exception { System.out.println(solve(new FastScanner())); }
}
'''

def source(body):
    return JAVA_SUPPORT.replace('%BODY%', body)

STARTER = '\n'.join(line for line in source(
    '    // Read the documented input using in.nextInt(), in.nextLong(), or in.next().\n'
    '    // Return the requested output. Only input/output helpers are supplied.\n'
    '    throw new UnsupportedOperationException("Implement solve");'
).splitlines() if not line.startswith(('  static long gcd(', '  static long power('))) + '\n'

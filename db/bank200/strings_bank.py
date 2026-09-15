from collections import Counter
from functools import lru_cache
from common import *

S=['abacaba','aaaa','z','ab','abcabcbb','pwwkew','banana','mississippi','abcdefghijklmnopqrstuvwxyz','aabbccddeeffggabacabadabacaba']
LAYOUT='One nonempty lowercase ASCII string s, of length at most 1000.'
READ='String s=in.next();int n=s.length();'

def ss(slug,title,task,oracle,body,d=5,cases=S,layout=LAYOUT):
    add(slug,title,d,task,layout,cases,words_input,oracle,READ+body)

def substr(s):
    return [s[i:j] for i in range(len(s)) for j in range(i+1,len(s)+1)]

ss('longest-unique-substring','Longest Unique Substring','Print the longest substring length with no repeated character.',
   lambda s:max(len(t) for t in substr(s) if len(set(t))==len(t)),
   'int[] last=new int[26];Arrays.fill(last,-1);int left=0,best=0;for(int i=0;i<n;i++){int c=s.charAt(i)-97;left=Math.max(left,last[c]+1);last[c]=i;best=Math.max(best,i-left+1);}return ""+best;')

K=[(s,str(k)) for s,k in zip(S,[2,1,1,1,2,3,2,3,5,4])]
add('substring-at-most-k-distinct','Substring with at Most K Distinct Characters',6,'Print the maximum substring length containing at most k distinct characters.',
    LAYOUT+' The next token is k (1 <= k <= 26).',K,words_input,
    lambda c:max([len(t) for t in substr(c[0]) if len(set(t))<=int(c[1])]+[0]),
    READ+'int k=in.nextInt(),left=0,count=0,best=0;int[] f=new int[26];for(int r=0;r<n;r++){if(f[s.charAt(r)-97]++==0)count++;while(count>k)if(--f[s.charAt(left++)-97]==0)count--;best=Math.max(best,r-left+1);}return ""+best;')

add('count-k-distinct-substrings','Count K-Distinct Substrings',6,'Count nonempty substrings containing exactly k distinct characters. Different start/end positions count separately.',
    LAYOUT+' The next token is k (1 <= k <= 26).',K,words_input,
    lambda c:sum(len(set(t))==int(c[1]) for t in substr(c[0])),
    READ+'int k=in.nextInt();long ans=0;for(int i=0;i<n;i++){int[] f=new int[26];int count=0;for(int j=i;j<n;j++){if(f[s.charAt(j)-97]++==0)count++;if(count==k)ans++;if(count>k)break;}}return ""+ans;')

PAIRS=[('listen','silent'),('aab','abb'),('a','a'),('a','b'),('abc','cba'),('paper','title'),('abac','acba'),('foo','bar'),('abcdefghijklmnopqrstuvwxyz','zyxwvutsrqponmlkjihgfedcba'),('aabbcc','abc')]
PAIRLAYOUT='Two nonempty lowercase ASCII strings s and t, each of length at most 1000, on separate lines.'
RP='String s=in.next(),t=in.next();'

def pair(slug,title,task,oracle,body,d=5,cases=PAIRS,layout=PAIRLAYOUT):
    add(slug,title,d,task,layout,cases,words_input,oracle,RP+body)

pair('anagram-equivalence','Anagram Equivalence','Print true if the strings contain the same characters with the same frequencies, otherwise false.',
     lambda c:Counter(c[0])==Counter(c[1]),
     'int[] f=new int[26];for(char x:s.toCharArray())f[x-97]++;for(char x:t.toCharArray())f[x-97]--;for(int x:f)if(x!=0)return "false";return "true";',4)

pair('subsequence-membership','Subsequence Membership','Print true if s can be obtained from t by deleting zero or more characters without reordering.',
     lambda c:(lambda it:all(x in it for x in c[0]))(iter(c[1])),
     'int j=0;for(int i=0;i<t.length()&&j<s.length();i++)if(s.charAt(j)==t.charAt(i))j++;return ""+(j==s.length());',4,
     cases=[('ace','abcde'),('axc','ahbgdc'),('a','a'),('a','b'),('aaa','aaaa'),('aaa','aa'),('abc','aabbcc'),('abc','cba'),('xyz','abcdefghijklmnopqrstuvwxaybz'),('code','challengeportal')])

pair('isomorphic-strings','Isomorphic Strings','Print true if a one-to-one character mapping transforms s into t. Different source characters cannot map to the same target character.',
     lambda c:len(c[0])==len(c[1]) and len(set(zip(*c)))==len(set(c[0]))==len(set(c[1])),
     'if(s.length()!=t.length())return "false";int[] a=new int[26],b=new int[26];for(int i=0;i<s.length();i++){int x=s.charAt(i)-97,y=t.charAt(i)-97;if(a[x]!=b[y])return "false";a[x]=b[y]=i+1;}return "true";')

pair('cyclic-string-rotation','Cyclic String Rotation','Print true if t is a cyclic rotation of s, including a rotation by zero positions.',
     lambda c:len(c[0])==len(c[1]) and c[1] in c[0]+c[0],
     'return ""+(s.length()==t.length()&&(s+s).contains(t));',4,
     cases=[('abcd','cdab'),('abcd','acbd'),('a','a'),('a','b'),('aaaa','aaaa'),('abc','bca'),('abc','ab'),('abab','baba'),('rotation','tionrota'),('abcdefghijk','kabcdefghij')])

pair('letter-supply','Letter Supply','Print true if t supplies enough copies of each character to construct s; otherwise false.',
     lambda c:not (Counter(c[0])-Counter(c[1])),
     'int[] f=new int[26];for(char x:t.toCharArray())f[x-97]++;for(char x:s.toCharArray())if(--f[x-97]<0)return "false";return "true";',4)

MATCH=[('cbaebabacd','abc'),('aaaaa','aa'),('a','a'),('a','b'),('ab','abc'),('abab','ab'),('mississippi','issi'),('banana','ana'),('abcdefghijk','ijk'),('abcabcabcabc','cab')]
pair('anagram-window-starts','Anagram Window Starts','Print all starting indices where a substring of s is an anagram of t, in ascending order. Print EMPTY if none.',
     lambda c:[i for i in range(len(c[0])-len(c[1])+1) if Counter(c[0][i:i+len(c[1])])==Counter(c[1])],
     'int[] a=new int[26],b=new int[26];for(char x:t.toCharArray())b[x-97]++;List<Integer>ans=new ArrayList<>();int k=t.length();for(int i=0;i<s.length();i++){a[s.charAt(i)-97]++;if(i>=k)a[s.charAt(i-k)-97]--;if(i>=k-1&&Arrays.equals(a,b))ans.add(i-k+1);}return join(ans);',6,cases=MATCH)

pair('overlapping-pattern-count','Overlapping Pattern Count','Count occurrences of t as a substring of s, including overlapping occurrences.',
     lambda c:sum(c[0].startswith(c[1],i) for i in range(len(c[0]))),
     'int m=t.length(),j=0,ans=0;int[] p=new int[m];for(int i=1;i<m;i++){int k=p[i-1];while(k>0&&t.charAt(i)!=t.charAt(k))k=p[k-1];if(t.charAt(i)==t.charAt(k))k++;p[i]=k;}for(int i=0;i<s.length();i++){while(j>0&&s.charAt(i)!=t.charAt(j))j=p[j-1];if(s.charAt(i)==t.charAt(j))j++;if(j==m){ans++;j=p[j-1];}}return ""+ans;',6,cases=MATCH)

ss('smallest-repeating-unit','Smallest Repeating Unit','Print the shortest prefix that can be repeated one or more times to form the whole string.',
   lambda s:next(s[:k] for k in range(1,len(s)+1) if len(s)%k==0 and s[:k]*(len(s)//k)==s),
   'int[] p=new int[n];for(int i=1;i<n;i++){int j=p[i-1];while(j>0&&s.charAt(i)!=s.charAt(j))j=p[j-1];if(s.charAt(i)==s.charAt(j))j++;p[i]=j;}int k=n-p[n-1];return n%k==0?s.substring(0,k):s;',6,
   cases=['ababab','abcab','a','aaaa','abcabc','abcd','xyzxyzxyz','abaaba','aabaabaabaab','abcdefghijklmnopqrstuvwxyz'])

ss('longest-proper-border','Longest Proper Border','Print the longest proper prefix that is also a suffix. Prefix and suffix may overlap. Print EMPTY if none.',
   lambda s:next((s[:k] for k in range(len(s)-1,0,-1) if s[:k]==s[-k:]),'EMPTY'),
   'int[] p=new int[n];for(int i=1;i<n;i++){int j=p[i-1];while(j>0&&s.charAt(i)!=s.charAt(j))j=p[j-1];if(s.charAt(i)==s.charAt(j))j++;p[i]=j;}return p[n-1]==0?"EMPTY":s.substring(0,p[n-1]);',6)

ss('distinct-substring-count','Distinct Substring Count','Print the number of distinct nonempty substrings. Equal text at different positions counts once.',
   lambda s:len(set(substr(s))),
   'String[] suffix=new String[n];for(int i=0;i<n;i++)suffix[i]=s.substring(i);Arrays.sort(suffix);long ans=0;for(int i=0;i<n;i++){int k=0;if(i>0)while(k<suffix[i].length()&&k<suffix[i-1].length()&&suffix[i].charAt(k)==suffix[i-1].charAt(k))k++;ans+=suffix[i].length()-k;}return ""+ans;',7)

ss('palindromic-substring-count','Palindromic Substring Count','Count nonempty palindromic substrings by their start/end positions.',
   lambda s:sum(t==t[::-1] for t in substr(s)),
   'long ans=0;for(int c=0;c<2*n-1;c++){int l=c/2,r=l+c%2;while(l>=0&&r<n&&s.charAt(l)==s.charAt(r)){ans++;l--;r++;}}return ""+ans;')

ss('prepend-to-palindrome','Prepend to Palindrome','Print the minimum number of characters that must be added only to the beginning to make s a palindrome.',
   lambda s:len(s)-max(k for k in range(1,len(s)+1) if s[:k]==s[:k][::-1]),
   'String t=s+"#"+new StringBuilder(s).reverse();int[] p=new int[t.length()];for(int i=1;i<t.length();i++){int j=p[i-1];while(j>0&&t.charAt(i)!=t.charAt(j))j=p[j-1];if(t.charAt(i)==t.charAt(j))j++;p[i]=j;}return ""+(n-p[t.length()-1]);',7)

def remove_pairs(s):
    while True:
        for i in range(len(s)-1):
            if s[i]==s[i+1]: s=s[:i]+s[i+2:]; break
        else: return s or 'EMPTY'
ss('cancel-adjacent-pairs','Cancel Adjacent Pairs','Repeatedly remove the leftmost adjacent pair of equal characters until no pair remains. Print the remaining string, or EMPTY.',remove_pairs,
   'StringBuilder b=new StringBuilder();for(char c:s.toCharArray()){int k=b.length();if(k>0&&b.charAt(k-1)==c)b.deleteCharAt(k-1);else b.append(c);}return b.length()==0?"EMPTY":b.toString();')

ss('run-length-encoding','Run-Length Encoding','Encode every maximal run as its character followed by its length, including length 1. Print the concatenation.',
   lambda s:''.join(k+str(len(list(v))) for k,v in __import__('itertools').groupby(s)),
   'StringBuilder b=new StringBuilder();for(int i=0;i<n;){int j=i+1;while(j<n&&s.charAt(j)==s.charAt(i))j++;b.append(s.charAt(i)).append(j-i);i=j;}return b.toString();',4)

def smallest_unique(s):
    if not s: return ''
    end=min(s.rindex(c) for c in set(s)); c=min(s[:end+1]); i=s.index(c)
    return c+smallest_unique(s[i+1:].replace(c,''))
ss('smallest-unique-subsequence','Smallest Unique Subsequence','Print the lexicographically smallest subsequence that contains each distinct character of s exactly once.',smallest_unique,
   'int[] left=new int[26];boolean[] used=new boolean[26];for(char c:s.toCharArray())left[c-97]++;StringBuilder b=new StringBuilder();for(char c:s.toCharArray()){left[c-97]--;if(used[c-97])continue;while(b.length()>0&&b.charAt(b.length()-1)>c&&left[b.charAt(b.length()-1)-97]>0){used[b.charAt(b.length()-1)-97]=false;b.deleteCharAt(b.length()-1);}b.append(c);used[c-97]=true;}return b.toString();',7)

ss('first-unique-character-index','First Unique Character Index','Print the index of the first character occurring exactly once, or -1.',
   lambda s:next((i for i,c in enumerate(s) if s.count(c)==1),-1),
   'int[] f=new int[26];for(char c:s.toCharArray())f[c-97]++;for(int i=0;i<n;i++)if(f[s.charAt(i)-97]==1)return ""+i;return "-1";',4)

def partition(s):
    ans=[]; start=0
    for i in range(len(s)):
        if not set(s[start:i+1]) & set(s[i+1:]): ans.append(i-start+1); start=i+1
    return ans
ss('independent-letter-partitions','Independent Letter Partitions','Partition s into as many nonempty contiguous parts as possible so each character appears in only one part. Print the part lengths.',partition,
   'int[] last=new int[26];for(int i=0;i<n;i++)last[s.charAt(i)-97]=i;int start=0,end=0;List<Integer>b=new ArrayList<>();for(int i=0;i<n;i++){end=Math.max(end,last[s.charAt(i)-97]);if(i==end){b.add(i-start+1);start=i+1;}}return join(b);',6)

B=['00110011','0101','0','1','0000','1111','000111','10101010','1100101001110001','0'*60+'1'*60]
BL='One nonempty binary string, of length at most 2000.'
ss('equal-zero-one-substring','Equal Zero-One Substring','Print the longest substring length containing equal numbers of zeros and ones, or 0.',
   lambda s:max([len(t) for t in substr(s) if t.count('0')==t.count('1')]+[0]),
   'Map<Integer,Integer>m=new HashMap<>();m.put(0,-1);int balance=0,best=0;for(int i=0;i<n;i++){balance+=s.charAt(i)==49?1:-1;if(m.containsKey(balance))best=Math.max(best,i-m.get(balance));else m.put(balance,i);}return ""+best;',6,cases=B,layout=BL)

ss('monotone-binary-flips','Monotone Binary Flips','Print the minimum bit flips to make the string nondecreasing (all zeros before all ones). Either group may be empty.',
   lambda s:min(s[:i].count('1')+s[i:].count('0') for i in range(len(s)+1)),
   'int ones=0,flips=0;for(char c:s.toCharArray())if(c==49)ones++;else flips=Math.min(flips+1,ones);return ""+flips;',5,cases=B,layout=BL)

pair('add-binary-strings','Add Binary Strings','Print the binary sum without leading zeros, except that zero is printed as 0.',
     lambda c:bin(int(c[0],2)+int(c[1],2))[2:],
     'int i=s.length()-1,j=t.length()-1,carry=0;StringBuilder b=new StringBuilder();while(i>=0||j>=0||carry>0){int v=carry+(i>=0?s.charAt(i--)-48:0)+(j>=0?t.charAt(j--)-48:0);b.append(v%2);carry=v/2;}b.reverse();int p=0;while(p<b.length()-1&&b.charAt(p)==48)p++;return b.substring(p);',5,
     cases=list(zip(B,reversed(B))),layout='Two nonempty binary strings of length at most 2000. Leading zeros are allowed.')

D=[('1432219','3'),('10200','1'),('10','2'),('9','0'),('12345','2'),('54321','2'),('1000','1'),('1111','2'),('9876543210','9'),('120120120','4')]
add('remove-k-digits','Remove K Digits',6,'Delete exactly k digits to obtain the smallest possible nonnegative decimal number without changing order. Print it without leading zeros, or 0.',
    'A decimal string s with no leading zeros, followed by k. 1 <= length(s) <= 2000; 0 <= k <= length(s).',D,words_input,
    lambda c:min(int(''.join(t) or '0') for t in __import__('itertools').combinations(c[0],len(c[0])-int(c[1]))),
    READ+'int k=in.nextInt();StringBuilder b=new StringBuilder();for(char c:s.toCharArray()){while(k>0&&b.length()>0&&b.charAt(b.length()-1)>c){b.deleteCharAt(b.length()-1);k--;}b.append(c);}b.setLength(b.length()-k);int p=0;while(p<b.length()&&b.charAt(p)==48)p++;return p==b.length()?"0":b.substring(p);')

V=['()[]{}','([)]','(',')','()','((()))','{[()()]}','(()','())(()','([]{})'*25]
def valid(s):
    while any(t in s for t in ['()','[]','{}']):
        for t in ['()','[]','{}']: s=s.replace(t,'')
    return not s
ss('balanced-bracket-types','Balanced Bracket Types','Print true if brackets are correctly nested and matched, otherwise false.',valid,
   'Deque<Character>q=new ArrayDeque<>();for(char c:s.toCharArray()){if(c==40||c==91||c==123)q.push(c);else{if(q.isEmpty())return "false";char x=q.pop();if((c==41&&x!=40)||(c==93&&x!=91)||(c==125&&x!=123))return "false";}}return ""+q.isEmpty();',5,cases=V,layout='One string of 1 to 2000 characters from ()[]{}.')

VP=['())','(((',')','(','()','((()))','(()','())(()','()()()','('*50+')'*49]
def inserts(s):
    bal=missing=0
    for c in s:
        bal+=1 if c=='(' else -1
        if bal<0: missing+=1; bal=0
    return bal+missing
ss('minimum-parenthesis-insertions','Minimum Parenthesis Insertions','Print the minimum parentheses insertions needed to make the string balanced.',inserts,
   'int bal=0,ans=0;for(char c:s.toCharArray()){if(c==40)bal++;else if(bal>0)bal--;else ans++;}return ""+(ans+bal);',4,cases=VP,layout='One nonempty string of at most 2000 parentheses ( and ).')

ss('longest-balanced-parenthesis-substring','Longest Balanced Parenthesis Substring','Print the length of the longest contiguous substring of correctly matched parentheses, or 0.',
   lambda s:max([len(t) for t in substr(s) if valid(t)]+[0]),
   'Deque<Integer>q=new ArrayDeque<>();q.push(-1);int best=0;for(int i=0;i<n;i++){if(s.charAt(i)==40)q.push(i);else{q.pop();if(q.isEmpty())q.push(i);else best=Math.max(best,i-q.peek());}}return ""+best;',7,cases=VP,layout='One nonempty string of at most 2000 parentheses ( and ).')

WC=[('adceb','*a*b'),('acdcb','a*c?b'),('a','?'),('a','b'),('abc','*'),('abc','a**c'),('abc','a?'),('mississippi','m*iss*?pi'),('aaaaaaaaab','a*a*a*b'),('abcabc','?b*bc')]
def wildcard(c):
    s,p=c
    @lru_cache(None)
    def f(i,j):
        if j==len(p): return i==len(s)
        if p[j]=='*': return f(i,j+1) or i<len(s) and f(i+1,j)
        return i<len(s) and p[j] in ('?',s[i]) and f(i+1,j+1)
    return f(0,0)
pair('wildcard-full-match','Wildcard Full Match','Print true if the entire text s matches pattern t. ? matches one character and * matches any sequence, including empty.',wildcard,
     'int n=s.length(),m=t.length();boolean[] d=new boolean[m+1];d[0]=true;for(int j=1;j<=m;j++)d[j]=d[j-1]&&t.charAt(j-1)==42;for(int i=1;i<=n;i++){boolean[] e=new boolean[m+1];for(int j=1;j<=m;j++){char c=t.charAt(j-1);e[j]=c==42?e[j-1]||d[j]:(c==63||c==s.charAt(i-1))&&d[j-1];}d=e;}return ""+d[m];',7,cases=WC,layout='A lowercase text s and a pattern t. Both have length 1 to 1000; t contains lowercase letters, ? and *.')

def palcuts(s):
    @lru_cache(None)
    def f(i):
        if i==len(s): return -1
        return min(1+f(j) for j in range(i+1,len(s)+1) if s[i:j]==s[i:j][::-1])
    return f(0)
ss('minimum-palindrome-cuts','Minimum Palindrome Cuts','Print the minimum cuts needed to partition s into nonempty palindromic substrings.',palcuts,
   'boolean[][] p=new boolean[n][n];int[] d=new int[n+1];d[0]=-1;for(int r=0;r<n;r++){d[r+1]=r;for(int l=r;l>=0;l--)if(s.charAt(l)==s.charAt(r)&&(r-l<2||p[l+1][r-1])){p[l][r]=true;d[r+1]=Math.min(d[r+1],d[l]+1);}}return ""+d[n];',7)

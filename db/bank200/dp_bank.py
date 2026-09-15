from functools import lru_cache
from itertools import product
from common import *

SMALL=[[2,7,9,3,1],[1,2,3],[0],[7],[0,0,0],[1,1,1,1],[3,2,5,10,7],[5,4,3,2,1],[2,4,6,8,10,12],[1,3,7,9,2,8,4,6,11,5,13,10]]
L='n followed by n nonnegative integers; 1 <= n <= 100, 0 <= a[i] <= 100.'
R='int n=in.nextInt();int[] a=array(in,n);'

def dpa(slug,title,task,oracle,body,d=6,cases=SMALL,layout=L):
    add(slug,title,d,task,layout,cases,arr_input,oracle,R+body)

def subset_sums(a):
    sums=[0]
    for x in a: sums += [s+x for s in sums]
    return sums

dpa('equal-sum-partition','Equal Sum Partition','Print true if the array can be split into two disjoint groups of equal sum, using every value. Empty groups are allowed.',
    lambda a:sum(a)%2==0 and sum(a)//2 in subset_sums(a),
    'int s=0;for(int x:a)s+=x;if(s%2!=0)return "false";boolean[] d=new boolean[s/2+1];d[0]=true;for(int x:a)for(int j=s/2;j>=x;j--)d[j]|=d[j-x];return ""+d[s/2];')

dpa('minimum-partition-difference','Minimum Partition Difference','Assign every value to one of two groups. Print the minimum absolute difference between their sums. Empty groups are allowed.',
    lambda a:min(abs(sum(a)-2*s) for s in subset_sums(a)),
    'int s=0;for(int x:a)s+=x;boolean[] d=new boolean[s/2+1];d[0]=true;for(int x:a)for(int j=s/2;j>=x;j--)d[j]|=d[j-x];for(int j=s/2;j>=0;j--)if(d[j])return ""+(s-2*j);return "0";')

KS=[(a,k) for a,k in zip(SMALL,[10,3,0,7,0,2,10,7,20,30])]
KL='n and target, then n values; 1 <= n <= 40, 0 <= a[i] <= 100, 0 <= target <= 10000.'
RK='int n=in.nextInt(),k=in.nextInt();int[] a=array(in,n);'
add('count-target-subsets','Count Target Subsets',6,'Count subsets of indices whose values sum to target. Include the empty subset. Print the count modulo 1000000007.',
    KL,KS,arrk_input,lambda c:subset_sums(c[0]).count(c[1])%1000000007,
    RK+'long[] d=new long[k+1];d[0]=1;for(int x:a)for(int j=k;j>=x;j--)d[j]=(d[j]+d[j-x])%MOD;return ""+d[k];')

add('target-sign-assignments','Target Sign Assignments',6,'Place a + or - before every value. Count assignments whose signed sum equals target, modulo 1000000007. Signs on zero count separately.',
    KL,KS,arrk_input,lambda c:sum(sum(x*y for x,y in zip(c[0],signs))==c[1] for signs in product([-1,1],repeat=len(c[0]))),
    RK+'Map<Integer,Long>d=new HashMap<>();d.put(0,1L);for(int x:a){Map<Integer,Long>e=new HashMap<>();for(Map.Entry<Integer,Long>z:d.entrySet()){int s=z.getKey();long v=z.getValue();e.put(s+x,(e.getOrDefault(s+x,0L)+v)%MOD);e.put(s-x,(e.getOrDefault(s-x,0L)+v)%MOD);}d=e;}return ""+d.getOrDefault(k,0L);')

def independent(a,circle=False):
    return max(sum(a[i] for i in range(len(a)) if mask>>i&1) for mask in range(1<<len(a))
               if not mask&(mask<<1) and (not circle or len(a)==1 or not(mask&1 and mask>>(len(a)-1)&1)))
dpa('nonadjacent-reward','Nonadjacent Reward','Choose nonadjacent array positions to maximize their total value. Choosing none is allowed. Print the maximum.',
    independent,'long skip=0,take=0;for(int x:a){long t=skip+x;skip=Math.max(skip,take);take=t;}return ""+Math.max(skip,take);',5)
dpa('circular-nonadjacent-reward','Circular Nonadjacent Reward','Positions form a circle, so the first and last are adjacent when n > 1. Choose nonadjacent positions for maximum total value; choosing none is allowed.',
    lambda a:independent(a,True),
    'if(n==1)return ""+a[0];long best=0;for(int start=0;start<=1;start++){long take=0,skip=0;for(int i=start;i<n-1+start;i++){long t=skip+a[i];skip=Math.max(skip,take);take=t;}best=Math.max(best,Math.max(skip,take));}return ""+best;')

def trading(a,fee=0,cool=False,limit=100):
    @lru_cache(None)
    def f(i,hold,left):
        if i>=len(a): return -10**12 if hold else 0
        best=f(i+1,hold,left)
        if hold and left: best=max(best,a[i]-fee+f(i+1+int(cool),False,left-1))
        if not hold and left: best=max(best,-a[i]+f(i+1,True,left))
        return best
    return f(0,False,limit)
dpa('stock-trades-with-cooldown','Stock Trades with Cooldown','Values are daily prices. Maximize profit with unlimited transactions and at most one held share. After selling, wait one whole day before buying again. Print maximum profit.',
    lambda a:trading(a,cool=True),
    'long hold=-a[0],sold=Long.MIN_VALUE/4,rest=0;for(int i=1;i<n;i++){long h=Math.max(hold,rest-a[i]),s=hold+a[i];rest=Math.max(rest,sold);hold=h;sold=s;}return ""+Math.max(rest,sold);')

FEES=[(a,i%4) for i,a in enumerate(SMALL)]
add('stock-trades-with-fee','Stock Trades with Fee',6,'Values are daily prices. Maximize profit with unlimited transactions and at most one held share. Charge fee once per sale; print maximum profit.',
    'n fee, then n prices. 1 <= n <= 2000; 0 <= fee, prices <= 1000000.',FEES,arrk_input,lambda c:trading(c[0],fee=c[1]),
    RK+'long cash=0,hold=-a[0];for(int i=1;i<n;i++){long old=cash;cash=Math.max(cash,hold+a[i]-k);hold=Math.max(hold,old-a[i]);}return ""+cash;')

dpa('at-most-two-stock-trades','At Most Two Stock Trades','Values are daily prices. Print maximum profit with at most two complete buy/sell transactions, holding at most one share at a time.',
    lambda a:trading(a,limit=2),
    'long b1=Long.MIN_VALUE/4,b2=b1,s1=0,s2=0;for(int x:a){b1=Math.max(b1,-x);s1=Math.max(s1,b1+x);b2=Math.max(b2,s1-x);s2=Math.max(s2,b2+x);}return ""+s2;',7)

def enum_inc(a,mode):
    seqs=[tuple(a[i] for i in range(len(a)) if mask>>i&1) for mask in range(1,1<<len(a))]
    inc=[s for s in seqs if all(x<y for x,y in zip(s,s[1:]))]
    if mode=='count': return sum(len(s)==max(map(len,inc)) for s in inc)
    if mode=='sum': return max(map(sum,inc))
    return max(len(s) for s in seqs if any(all(s[i]<s[i+1] for i in range(k)) and all(s[i]>s[i+1] for i in range(k,len(s)-1)) for k in range(len(s))))
dpa('count-longest-increasing-subsequences','Count Longest Increasing Subsequences','Count strictly increasing subsequences having maximum possible length, modulo 1000000007. Different index selections count separately.',
    lambda a:enum_inc(a,'count'),
    'int[] len=new int[n];long[] ways=new long[n];int best=0;long ans=0;for(int i=0;i<n;i++){len[i]=1;ways[i]=1;for(int j=0;j<i;j++)if(a[j]<a[i]){if(len[j]+1>len[i]){len[i]=len[j]+1;ways[i]=ways[j];}else if(len[j]+1==len[i])ways[i]=(ways[i]+ways[j])%MOD;}if(len[i]>best){best=len[i];ans=ways[i];}else if(len[i]==best)ans=(ans+ways[i])%MOD;}return ""+ans;',7)
dpa('maximum-sum-increasing-subsequence','Maximum Sum Increasing Subsequence','Print the largest sum of a nonempty strictly increasing subsequence.',
    lambda a:enum_inc(a,'sum'),
    'long[] d=new long[n];long best=0;for(int i=0;i<n;i++){d[i]=a[i];for(int j=0;j<i;j++)if(a[j]<a[i])d[i]=Math.max(d[i],d[j]+a[i]);best=Math.max(best,d[i]);}return ""+best;')
dpa('longest-bitonic-subsequence','Longest Bitonic Subsequence','Print the longest subsequence length that first strictly increases and then strictly decreases. Either phase may have length zero.',
    lambda a:enum_inc(a,'bitonic'),
    'int[] u=new int[n],v=new int[n];Arrays.fill(u,1);Arrays.fill(v,1);for(int i=0;i<n;i++)for(int j=0;j<i;j++)if(a[j]<a[i])u[i]=Math.max(u[i],u[j]+1);for(int i=n-1;i>=0;i--)for(int j=i+1;j<n;j++)if(a[j]<a[i])v[i]=Math.max(v[i],v[j]+1);int best=1;for(int i=0;i<n;i++)best=Math.max(best,u[i]+v[i]-1);return ""+best;',7)

PS=[('abcde','ace'),('abc','def'),('a','a'),('a','b'),('aaaa','aa'),('banana','ananas'),('aggtab','gxtxayb'),('abcdef','fedcba'),('aabbaa','abaaba'),('programming','algorithm')]
PL='Two nonempty lowercase ASCII strings s and t, each with length at most 500.'
RP='String s=in.next(),t=in.next();int n=s.length(),m=t.length();'
def lcs(s,t):
    @lru_cache(None)
    def f(i,j):
        if i==len(s) or j==len(t): return 0
        return 1+f(i+1,j+1) if s[i]==t[j] else max(f(i+1,j),f(i,j+1))
    return f(0,0)
LCJAVA='int[][] d=new int[n+1][m+1];for(int i=1;i<=n;i++)for(int j=1;j<=m;j++)d[i][j]=s.charAt(i-1)==t.charAt(j-1)?1+d[i-1][j-1]:Math.max(d[i-1][j],d[i][j-1]);'
add('longest-common-subsequence','Longest Common Subsequence',6,'Print the length of the longest sequence that is a subsequence of both strings.',PL,PS,words_input,lambda c:lcs(*c),RP+LCJAVA+'return ""+d[n][m];')
add('shortest-common-supersequence-length','Shortest Common Supersequence Length',6,'Print the shortest length of a string containing both s and t as subsequences.',PL,PS,words_input,lambda c:len(c[0])+len(c[1])-lcs(*c),RP+LCJAVA+'return ""+(n+m-d[n][m]);')
add('longest-common-substring-length','Longest Common Substring Length',6,'Print the longest length of a contiguous substring present in both strings, or 0.',PL,PS,words_input,
    lambda c:max([j-i for i in range(len(c[0])) for j in range(i+1,len(c[0])+1) if c[0][i:j] in c[1]]+[0]),
    RP+'int[][] d=new int[n+1][m+1];int best=0;for(int i=1;i<=n;i++)for(int j=1;j<=m;j++)if(s.charAt(i-1)==t.charAt(j-1)){d[i][j]=1+d[i-1][j-1];best=Math.max(best,d[i][j]);}return ""+best;')

def distinct(c):
    s,t=c
    return sum(''.join(s[i] for i in range(len(s)) if mask>>i&1)==t for mask in range(1<<len(s)))
add('distinct-subsequence-embeddings','Distinct Subsequence Embeddings',7,'Count index selections in s that spell t, modulo 1000000007.',PL,PS,words_input,distinct,
    RP+'long[] d=new long[m+1];d[0]=1;for(int i=0;i<n;i++)for(int j=m;j>0;j--)if(s.charAt(i)==t.charAt(j-1))d[j]=(d[j]+d[j-1])%MOD;return ""+d[m];')

STR=[p[0] for p in PS]; STR[3]='b'; STR[4]='aaaba'
add('minimum-insertions-palindrome','Minimum Insertions to Palindrome',6,'Insert characters anywhere to make s a palindrome. Print the minimum insertions.',
    'One nonempty lowercase ASCII string of length at most 500.',STR,words_input,lambda s:len(s)-lcs(s,s[::-1]),
    'String s=in.next();int n=s.length();int[][] d=new int[n][n];for(int len=2;len<=n;len++)for(int i=0;i+len<=n;i++){int j=i+len-1;d[i][j]=s.charAt(i)==s.charAt(j)?(len==2?0:d[i+1][j-1]):1+Math.min(d[i+1][j],d[i][j-1]);}return ""+d[0][n-1];')

IC=[('aabcc','dbbca','aadbbcbcac'),('aabcc','dbbca','aadbbbaccc'),('a','b','ab'),('a','b','ba'),('a','a','aa'),('ab','cd','acbd'),('ab','cd','adbc'),('abc','xyz','axbycz'),('abc','xyz','abcxyz'),('aaab','aaba','aaaaabba')]
def interleave(c):
    a,b,s=c
    @lru_cache(None)
    def f(i,j):
        if i+j==len(s): return i==len(a) and j==len(b)
        return (i<len(a) and a[i]==s[i+j] and f(i+1,j)) or (j<len(b) and b[j]==s[i+j] and f(i,j+1))
    return len(s)==len(a)+len(b) and f(0,0)
add('interleaving-strings','Interleaving Strings',7,'Print true if s can be formed by interleaving all characters of a and b while preserving the order within each.',
    'Three lowercase strings a, b, s on separate lines. Each has length 1 to 500.',IC,words_input,interleave,
    'String a=in.next(),b=in.next(),s=in.next();int n=a.length(),m=b.length();if(s.length()!=n+m)return "false";boolean[][]d=new boolean[n+1][m+1];d[0][0]=true;for(int i=0;i<=n;i++)for(int j=0;j<=m;j++){if(i>0)d[i][j]|=d[i-1][j]&&a.charAt(i-1)==s.charAt(i+j-1);if(j>0)d[i][j]|=d[i][j-1]&&b.charAt(j-1)==s.charAt(i+j-1);}return ""+d[n][m];')

def frog(c):
    a,k=c
    @lru_cache(None)
    def f(i):
        return 0 if i==0 else min(f(j)+abs(a[i]-a[j]) for j in range(max(0,i-k),i))
    return f(len(a)-1)
add('bounded-frog-jumps','Bounded Frog Jumps',6,'Start at index 0 and reach n-1 by jumping forward between 1 and k indices. Jump cost is the absolute difference in heights. Print minimum total cost.',
    'n k, followed by n heights. 1 <= n <= 2000; 1 <= k <= 100; 0 <= heights <= 1000000.',[(a,i%4+1) for i,a in enumerate(SMALL)],arrk_input,frog,
    RK+'long[] d=new long[n];for(int i=1;i<n;i++){d[i]=Long.MAX_VALUE;for(int j=Math.max(0,i-k);j<i;j++)d[i]=Math.min(d[i],d[j]+Math.abs((long)a[i]-a[j]));}return ""+d[n-1];')

def climb(a):
    @lru_cache(None)
    def f(i):
        return 0 if i>=len(a) else a[i]+min(f(i+1),f(i+2))
    return min(f(0),f(1))
dpa('minimum-stair-cost','Minimum Stair Cost','Each value is a stair cost. Start on stair 0 or 1, pay when stepping on a stair, and move up 1 or 2 stairs. Print minimum cost to reach index n (just above the last stair).',climb,
    'long x=0,y=0;for(int i=2;i<=n;i++){long z=Math.min(y+a[i-1],x+a[i-2]);x=y;y=z;}return ""+y;',5)

ITEMS=[([(2,3),(3,4),(4,5)],5), ([(5,10)],4), ([(1,7)],1), ([(1,2),(1,3)],1), ([(3,9),(4,10)],0), ([(2,0),(3,0)],8), ([(2,4),(2,5),(2,6)],4), ([(5,20),(4,15),(1,3)],6), ([(i%5+1,i*3%17) for i in range(12)],13), ([(i%7+1,i*7%23) for i in range(15)],30)]
def item_input(c):
    a,k=c
    return f'{len(a)} {k}\n'+''.join(f'{w} {v}\n' for w,v in a)
def knapsack(c):
    a,k=c
    return max(sum(a[i][1] for i in range(len(a)) if mask>>i&1) for mask in range(1<<len(a)) if sum(a[i][0] for i in range(len(a)) if mask>>i&1)<=k)
add('zero-one-knapsack','Zero-One Knapsack',6,'Select each item at most once to maximize total value without exceeding capacity. Print the maximum value.',
    'n capacity, followed by n lines weight value. 1 <= n <= 100; 0 <= capacity <= 10000; 1 <= weight <= 1000; 0 <= value <= 1000000.',ITEMS,item_input,knapsack,
    'int n=in.nextInt(),k=in.nextInt();long[] d=new long[k+1];for(int i=0;i<n;i++){int w=in.nextInt(),v=in.nextInt();for(int j=k;j>=w;j--)d[j]=Math.max(d[j],d[j-w]+v);}return ""+d[k];')

COINS=[([1,2,5],5),([2],3),([1],0),([1],7),([2,3,7],12),([3,5],15),([4,6],7),([1,3,4],10),([2,5,10,20],60),([1,2,3,4,5],35)]
def combinations_count(c):
    a,k=c
    @lru_cache(None)
    def f(i,s):
        if i==len(a): return int(s==0)
        return sum(f(i+1,s-j*a[i]) for j in range(s//a[i]+1))%1000000007
    return f(0,k)
add('coin-combination-count','Coin Combination Count',6,'Count unordered ways to make target using unlimited copies of distinct coin denominations, modulo 1000000007. Target zero has one way.',
    'n target, then n distinct positive denominations. 1 <= n <= 50; 0 <= target <= 10000; 1 <= coin <= 1000.',COINS,arrk_input,combinations_count,
    RK+'long[] d=new long[k+1];d[0]=1;for(int x:a)for(int j=x;j<=k;j++)d[j]=(d[j]+d[j-x])%MOD;return ""+d[k];')

def rod(a):
    @lru_cache(None)
    def f(n): return 0 if n==0 else max(a[j-1]+f(n-j) for j in range(1,n+1))
    return f(len(a))
dpa('rod-cutting-revenue','Rod Cutting Revenue','a[i] is the price of a piece of length i+1. Cut a rod of length n into positive integer lengths to maximize revenue. Print the maximum.',rod,
    'long[] d=new long[n+1];for(int i=1;i<=n;i++)for(int j=1;j<=i;j++)d[i]=Math.max(d[i],d[i-j]+a[j-1]);return ""+d[n];')

DIMS=[[10,30,5,60],[10,20,30],[2,3],[1,1,1,1],[5,10,3,12,5,50,6],[30,35,15,5,10,20,25],[100,1,100,1],[2,4,8,16,32],[7,3,9,2,8],[3,7,4,5,9,2,6,8]]
def matrix(a):
    @lru_cache(None)
    def f(i,j): return 0 if j-i<=1 else min(f(i,k)+f(k,j)+a[i]*a[k]*a[j] for k in range(i+1,j))
    return f(0,len(a)-1)
dpa('matrix-chain-multiplication','Matrix Chain Multiplication','n dimensions describe n-1 matrices; matrix i has dimensions a[i] by a[i+1]. Print the minimum scalar multiplications required to multiply the chain.',matrix,
    'long[][]d=new long[n][n];for(int len=2;len<n;len++)for(int i=0;i+len<n;i++){int j=i+len;d[i][j]=Long.MAX_VALUE;for(int k=i+1;k<j;k++)d[i][j]=Math.min(d[i][j],d[i][k]+d[k][j]+(long)a[i]*a[k]*a[j]);}return ""+d[0][n-1];',7,cases=DIMS,layout='n followed by n dimensions. 2 <= n <= 100; 1 <= a[i] <= 1000.')

BALLOONS=[[3,1,5,8],[1,5],[0],[7],[0,0,0],[1,1,1,1],[2,4,3],[9,1,9],[1,2,3,4,5],[5,2,6,1,3,4]]
def burst(a):
    @lru_cache(None)
    def f(t):
        if not t: return 0
        return max((t[i-1] if i else 1)*v*(t[i+1] if i+1<len(t) else 1)+f(t[:i]+t[i+1:]) for i,v in enumerate(t))
    return f(tuple(a))
dpa('burst-balloons-reward','Burst Balloons Reward','Remove all values one at a time. Removing a value gains its value times its current two neighbors; a missing neighbor has value 1. Print maximum total reward.',burst,
    'int[] b=new int[n+2];b[0]=b[n+1]=1;System.arraycopy(a,0,b,1,n);long[][]d=new long[n+2][n+2];for(int len=1;len<=n;len++)for(int l=1;l+len-1<=n;l++){int r=l+len-1;for(int k=l;k<=r;k++)d[l][r]=Math.max(d[l][r],d[l][k-1]+d[k+1][r]+(long)b[l-1]*b[k]*b[r+1]);}return ""+d[1][n];',8,cases=BALLOONS,layout='n followed by n values. 1 <= n <= 100; 0 <= a[i] <= 100.')

def endgame(a):
    @lru_cache(None)
    def f(l,r):
        if l>r: return 0
        return max(a[l]-f(l+1,r),a[r]-f(l,r-1))
    return (sum(a)+f(0,len(a)-1))//2
dpa('optimal-end-picking-game','Optimal End-Picking Game','Two players alternate taking either end of the remaining array and adding it to their score. Both maximize their own total. Print the first player maximum guaranteed total.',endgame,
    'long[][]d=new long[n][n];long total=0;for(int i=0;i<n;i++){d[i][i]=a[i];total+=a[i];}for(int len=2;len<=n;len++)for(int l=0;l+len<=n;l++){int r=l+len-1;d[l][r]=Math.max(a[l]-d[l+1][r],a[r]-d[l][r-1]);}return ""+((total+d[0][n-1])/2);',7)

EGGS=[(2,10),(1,7),(1,0),(1,1),(2,1),(2,100),(3,14),(3,50),(4,100),(5,200)]
def eggs(c):
    e,n=c
    @lru_cache(None)
    def f(e,n):
        if n<=1 or e==1: return n
        return 1+min(max(f(e-1,k-1),f(e,n-k)) for k in range(1,n+1))
    return f(e,n)
add('minimum-egg-drop-trials','Minimum Egg-Drop Trials',8,'Find an unknown safe floor threshold from 0 through floors using eggs that break above it and survive otherwise. Print minimum trials guaranteeing the answer in the worst case.',
    'eggs floors. 1 <= eggs <= 20; 0 <= floors <= 1000000.',EGGS,numbers_input,eggs,
    'int e=in.nextInt(),n=in.nextInt(),moves=0;long[] d=new long[e+1];while(d[e]<n){moves++;for(int j=e;j>0;j--)d[j]=Math.min(n,d[j]+d[j-1]+1);}return ""+moves;')

def schedule_input(a): return str(len(a))+'\n'+''.join(' '.join(map(str,row))+'\n' for row in a)
ACT=[[(1,2,3),(3,2,1)],[(5,5,5)],[(0,0,0)],[(1,0,0),(1,0,0)],[(10,1,1),(1,10,1),(1,1,10)],[(i%3,i%5,i%7) for i in range(5)],[(3,2,1)]*4,[(0,9,0)]*3,[(5,6,7),(7,6,5),(8,2,4)],[(i*3%11,i*5%13,i*7%17) for i in range(8)]]
def activity(a):
    return max(sum(a[i][c] for i,c in enumerate(cs)) for cs in product(range(3),repeat=len(a)) if all(x!=y for x,y in zip(cs,cs[1:])))
add('three-activity-schedule','Three-Activity Schedule',6,'Each day offers three activity rewards. Choose one per day, never choosing the same activity on consecutive days. Print maximum total reward.',
    'n, then n lines with three rewards. 1 <= n <= 2000; 0 <= reward <= 1000000.',ACT,schedule_input,activity,
    'int n=in.nextInt();long[]d=new long[3];for(int i=0;i<n;i++){long[]e=new long[3];for(int j=0;j<3;j++)e[j]=in.nextInt()+Math.max(d[(j+1)%3],d[(j+2)%3]);d=e;}return ""+Math.max(d[0],Math.max(d[1],d[2]));')

JOBS=[[(1,3,50),(2,4,10),(3,5,40)],[(0,1,5)],[(0,3,0)],[(0,2,7),(2,4,8)],[(1,5,10),(2,3,20)],[(i,i+2,i+1) for i in range(6)],[(0,9,50),(1,2,10),(2,3,10),(3,4,40)],[(1,2,3),(1,2,4),(1,2,5)],[(i,i+1,2*i) for i in range(8)],[(i%5,i%5+2,i*7%19) for i in range(10)]]
def jobs(a):
    return max(sum(j[2] for j in chosen) for mask in range(1<<len(a)) for chosen in [[a[i] for i in range(len(a)) if mask>>i&1]] if all(x[1]<=y[0] or y[1]<=x[0] for x,y in __import__('itertools').combinations(chosen,2)))
add('weighted-job-scheduling','Weighted Job Scheduling',7,'Choose nonoverlapping jobs for maximum profit. A job ending at time t is compatible with one starting at t. Print maximum profit.',
    'n, then n lines start end profit. 1 <= n <= 2000; 0 <= start < end <= 1000000; 0 <= profit <= 1000000.',JOBS,schedule_input,jobs,
    'int n=in.nextInt();int[][]a=new int[n][3];for(int[]r:a)for(int j=0;j<3;j++)r[j]=in.nextInt();Arrays.sort(a,Comparator.comparingInt(r->r[1]));long[]d=new long[n+1];for(int i=0;i<n;i++){int l=0,r=i;while(l<r){int m=(l+r)/2;if(a[m][1]<=a[i][0])l=m+1;else r=m;}d[i+1]=Math.max(d[i],d[l]+a[i][2]);}return ""+d[n];')

SQUARES=[12,13,1,2,3,4,7,25,99,250]
def squares(n):
    reachable={0}
    for count in range(1,n+1):
        reachable={s+x*x for s in reachable for x in range(1,__import__('math').isqrt(n-s)+1)}
        if n in reachable: return count
add('minimum-perfect-squares','Minimum Perfect Squares',6,'Print the smallest number of positive perfect squares whose sum is n. Repeated squares are allowed.',
    'One integer n, 1 <= n <= 10000.',SQUARES,numbers_input,squares,
    'int n=in.nextInt();int[]d=new int[n+1];Arrays.fill(d,n);d[0]=0;for(int i=1;i<=n;i++)for(int j=1;j*j<=i;j++)d[i]=Math.min(d[i],d[i-j*j]+1);return ""+d[n];')

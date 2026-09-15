from collections import Counter
from itertools import combinations
from common import *

A = arrays(11)
P = arrays(12, True)
READ = 'int n=in.nextInt(); int[] a=array(in,n);'
READK = 'int n=in.nextInt(), k=in.nextInt(); int[] a=array(in,n);'
KLAYOUT = 'n and k, followed by n integers. 1 <= n <= 2000; -1000000 <= a[i], k <= 1000000.'

def aa(slug, title, task, oracle, java, difficulty=5, cases=A, positive=False):
    add(slug, title, difficulty, task, POS_ARRAY_LAYOUT if positive else ARRAY_LAYOUT,
        cases, arr_input, oracle, READ + java)

aa('maximum-subarray-sum', 'Maximum Subarray Sum',
   'Print the largest sum of a nonempty contiguous subarray.',
   lambda a:max(sum(a[i:j]) for i in range(len(a)) for j in range(i+1,len(a)+1)),
   'long cur=a[0],best=cur;for(int i=1;i<n;i++){cur=Math.max(a[i],cur+a[i]);best=Math.max(best,cur);}return ""+best;')

aa('products-excluding-self', 'Products Excluding Self',
   'For each index, print the product of all other values modulo 1000000007, without division. The empty product is 1.',
   lambda a:[__import__('math').prod(a[:i]+a[i+1:])%1000000007 for i in range(len(a))],
   'long[] b=new long[n];long p=1;for(int i=0;i<n;i++){b[i]=p;p=p*a[i]%MOD;}p=1;for(int i=n-1;i>=0;i--){b[i]=b[i]*p%MOD;p=p*a[i]%MOD;}return join(b);',
   cases=P, positive=True)

R=[(a,i*7) for i,a in enumerate(A)]
add('rotate-array-right','Rotate Array Right',4,'Rotate the array right by k positions and print it. k may exceed n.',
    KLAYOUT+' k >= 0.',R,arrk_input,lambda c:c[0][- (c[1]%len(c[0])):]+c[0][:-(c[1]%len(c[0]))] if c[1]%len(c[0]) else c[0],
    READK+'int[] b=new int[n];for(int i=0;i<n;i++)b[(i+k%n)%n]=a[i];return join(b);')

aa('count-array-inversions','Count Array Inversions','Count pairs i < j for which a[i] > a[j]. Equal values are not inversions.',
   lambda a:sum(x>y for x,y in combinations(a,2)),
   'long count=0;int[] b=a.clone();Arrays.sort(b);int[] bit=new int[n+1];for(int i=n-1;i>=0;i--){int l=0,r=n;while(l<r){int m=(l+r)/2;if(b[m]<a[i])l=m+1;else r=m;}int p=l+1;for(int j=p-1;j>0;j-=j&-j)count+=bit[j];for(int j=p;j<=n;j+=j&-j)bit[j]++;}return ""+count;',7)

aa('strict-majority','Strict Majority Element','Print the value appearing more than n/2 times, or NONE if it does not exist.',
   lambda a:next((str(x) for x,c in Counter(a).items() if c>len(a)//2),'NONE'),
   'int v=0,c=0;for(int x:a){if(c==0)v=x;c+=x==v?1:-1;}c=0;for(int x:a)if(x==v)c++;return c>n/2?""+v:"NONE";',4)

aa('first-missing-positive','First Missing Positive','Print the smallest positive integer absent from the array. Aim for linear time and constant extra space.',
   lambda a:next(x for x in range(1,len(a)+2) if x not in a),
   'for(int i=0;i<n;i++)while(a[i]>0&&a[i]<=n&&a[a[i]-1]!=a[i]){int j=a[i]-1,t=a[j];a[j]=a[i];a[i]=t;}for(int i=0;i<n;i++)if(a[i]!=i+1)return ""+(i+1);return ""+(n+1);',7)

T=[(a,k) for a,k in zip(A,[5,4,0,14,0,11,-2,4,3,17])]
add('count-target-pairs','Count Target Pairs',5,'Count index pairs i < j whose values sum to k. Count separate pairs even when values repeat.',
    KLAYOUT,T,arrk_input,lambda c:sum(x+y==c[1] for x,y in combinations(c[0],2)),
    READK+'Map<Integer,Long> f=new HashMap<>();long ans=0;for(int x:a){ans+=f.getOrDefault(k-x,0L);f.put(x,f.getOrDefault(x,0L)+1);}return ""+ans;')

aa('distinct-zero-sum-triplets','Distinct Zero-Sum Triplets','Count distinct value triplets x <= y <= z summing to zero that can be formed using three different indices.',
   lambda a:len({tuple(sorted(t)) for t in combinations(a,3) if sum(t)==0}),
   'Arrays.sort(a);long ans=0;for(int i=0;i<n-2;i++){if(i>0&&a[i]==a[i-1])continue;int l=i+1,r=n-1;while(l<r){long s=(long)a[i]+a[l]+a[r];if(s<0)l++;else if(s>0)r--;else{ans++;int x=a[l],y=a[r];while(l<r&&a[l]==x)l++;while(l<r&&a[r]==y)r--;}}}return ""+ans;',6)

aa('leftmost-balance-index','Leftmost Balance Index','Print the first index with equal sums strictly to its left and right, or -1.',
   lambda a:next((i for i in range(len(a)) if sum(a[:i])==sum(a[i+1:])),-1),
   'long right=0,left=0;for(int x:a)right+=x;for(int i=0;i<n;i++){right-=a[i];if(left==right)return ""+i;left+=a[i];}return "-1";',4)

MP=[[2,3,-2,4],[-2,0,-1],[-5],[0],[1,-1,-1],[-2,-3,-4],[-1]*19,[2]*20,[0,2,-3,0,-2,-4],[-2,1,-2,1,-2,1,-2]]
aa('maximum-product-subarray','Maximum Product Subarray','Print the largest product of a nonempty contiguous subarray. For this problem n <= 20 and -5 <= a[i] <= 5.',
   lambda a:max(__import__('math').prod(a[i:j]) for i in range(len(a)) for j in range(i+1,len(a)+1)),
   'long hi=a[0],lo=hi,best=hi;for(int i=1;i<n;i++){long x=a[i],p=hi*x,q=lo*x;hi=Math.max(x,Math.max(p,q));lo=Math.min(x,Math.min(p,q));best=Math.max(best,hi);}return ""+best;',6,cases=MP)

M=[(a,k) for a,k in zip(P,[7,4,1,7,1,20,30,18,120,20000])]
add('shortest-target-sum-window','Shortest Target Sum Window',6,'Print the shortest nonempty subarray length with sum at least k, or 0 if none. All array values are nonnegative and k > 0.',
    'n k, then n values. 1 <= n <= 2000; 0 <= a[i] <= 1000000; 1 <= k <= 1000000000.',M,arrk_input,
    lambda c:min([j-i for i in range(len(c[0])) for j in range(i+1,len(c[0])+1) if sum(c[0][i:j])>=c[1]] or [0]),
    READK+'int l=0,best=n+1;long s=0;for(int r=0;r<n;r++){s+=a[r];while(s>=k){best=Math.min(best,r-l+1);s-=a[l++];}}return ""+(best==n+1?0:best);')

aa('longest-consecutive-values','Longest Consecutive Values','Print the length of the longest run of consecutive integer values present in any order. Duplicates do not extend a run.',
   lambda a:max((next((j for j in range(1,len(a)+1) if x+j not in a),len(a)) for x in set(a)),default=0),
   'Set<Integer>s=new HashSet<>();for(int x:a)s.add(x);int best=0;for(int x:s)if(!s.contains(x-1)){int y=x;while(s.contains(y))y++;best=Math.max(best,y-x);}return ""+best;',6)

aa('next-greater-value','Next Greater Value','For every position, print the first strictly greater value to its right, or NONE. Output n tokens.',
   lambda a:' '.join(str(next((x for x in a[i+1:] if x>a[i]),'NONE')) for i in range(len(a))),
   'String[] b=new String[n];Deque<Integer>s=new ArrayDeque<>();for(int i=n-1;i>=0;i--){while(!s.isEmpty()&&s.peek()<=a[i])s.pop();b[i]=s.isEmpty()?"NONE":""+s.peek();s.push(a[i]);}return String.join(" ",b);',5)

aa('warmer-day-distance','Warmer Day Distance','Treat values as daily temperatures. For every day print how many days until a strictly warmer day, or 0.',
   lambda a:[next((j-i for j in range(i+1,len(a)) if a[j]>a[i]),0) for i in range(len(a))],
   'int[] b=new int[n];Deque<Integer>s=new ArrayDeque<>();for(int i=0;i<n;i++){while(!s.isEmpty()&&a[i]>a[s.peek()]){int j=s.pop();b[j]=i-j;}s.push(i);}return join(b);')

aa('single-stock-trade','Single Stock Trade','Print the maximum profit from buying once and selling on a later day, or 0 if no profitable trade exists.',
   lambda a:max([a[j]-a[i] for i in range(len(a)) for j in range(i+1,len(a))]+[0]),
   'int low=a[0];long best=0;for(int x:a){best=Math.max(best,(long)x-low);low=Math.min(low,x);}return ""+best;',4,cases=P,positive=True)

aa('unlimited-stock-trades','Unlimited Stock Trades','Print the maximum profit with unlimited buy/sell transactions, holding at most one share at a time. No fees or cooldown.',
   lambda a:sum(max(0,y-x) for x,y in zip(a,a[1:])),
   'long ans=0;for(int i=1;i<n;i++)ans+=Math.max(0,a[i]-a[i-1]);return ""+ans;',4,cases=P,positive=True)

aa('maximum-sortable-chunks','Maximum Sortable Chunks','Partition into the maximum number of nonempty contiguous chunks such that sorting each chunk separately and concatenating gives the fully sorted array. Print the number of chunks.',
   lambda a:sum(sorted(a[:i])==sorted(a)[:i] for i in range(1,len(a)+1)),
   'int[] b=a.clone();Arrays.sort(b);Map<Integer,Integer>m=new HashMap<>();int ans=0;for(int i=0;i<n;i++){m.put(a[i],m.getOrDefault(a[i],0)+1);if(m.get(a[i])==0)m.remove(a[i]);m.put(b[i],m.getOrDefault(b[i],0)-1);if(m.get(b[i])==0)m.remove(b[i]);if(m.isEmpty())ans++;}return ""+ans;',7)

aa('shortest-unsorted-segment','Shortest Unsorted Segment','Print the length of the shortest contiguous segment that can be sorted to make the entire array nondecreasing. Print 0 if already sorted.',
   lambda a:(lambda ix:ix[-1]-ix[0]+1 if ix else 0)([i for i,(x,y) in enumerate(zip(a,sorted(a))) if x!=y]),
   'int l=n,r=-1,mx=a[0],mn=a[n-1];for(int i=0;i<n;i++){mx=Math.max(mx,a[i]);if(a[i]<mx)r=i;int j=n-1-i;mn=Math.min(mn,a[j]);if(a[j]>mn)l=j;}return ""+(r<0?0:r-l+1);',6)

aa('shortest-degree-segment','Shortest Degree Segment','The degree is the highest frequency of any value. Print the shortest subarray length having the same degree as the whole array.',
   lambda a:min(len(a)-1-a[::-1].index(x)-a.index(x)+1 for x,c in Counter(a).items() if c==max(Counter(a).values())),
   'Map<Integer,Integer>f=new HashMap<>(),first=new HashMap<>();int degree=0,best=n;for(int i=0;i<n;i++){first.putIfAbsent(a[i],i);int c=f.getOrDefault(a[i],0)+1;f.put(a[i],c);int len=i-first.get(a[i])+1;if(c>degree){degree=c;best=len;}else if(c==degree)best=Math.min(best,len);}return ""+best;')

add('nearby-equal-values','Nearby Equal Values',4,'Print true if equal values occur at different indices at distance at most k; otherwise false.',
    KLAYOUT+' k >= 0.',R,arrk_input,lambda c:any(c[0][i]==c[0][j] and j-i<=c[1] for i in range(len(c[0])) for j in range(i+1,len(c[0]))),
    READK+'Map<Integer,Integer>last=new HashMap<>();for(int i=0;i<n;i++){Integer p=last.put(a[i],i);if(p!=null&&i-p<=k)return "true";}return "false";')

E=[a if len(a)%2==0 else a+[8] for a in A]
aa('maximize-pair-minimums','Maximize Pair Minimums','n is even. Pair all values to maximize the sum of the smaller value in each pair. Print this maximum sum.',
   lambda a:sum(sorted(a)[::2]),'Arrays.sort(a);long s=0;for(int i=0;i<n;i+=2)s+=a[i];return ""+s;',5,cases=E)

aa('equalize-by-unit-moves','Equalize by Unit Moves','One move increments or decrements one value by 1. Print the minimum moves to make all values equal.',
   lambda a:min(sum(abs(x-v) for x in a) for v in a),
   'Arrays.sort(a);long s=0;for(int x:a)s+=Math.abs((long)x-a[n/2]);return ""+s;')

aa('maximum-sorted-gap','Maximum Sorted Gap','Print the largest gap between adjacent values in sorted order, or 0 for one value.',
   lambda a:max([y-x for x,y in zip(sorted(a),sorted(a)[1:])]+[0]),
   'Arrays.sort(a);long best=0;for(int i=1;i<n;i++)best=Math.max(best,(long)a[i]-a[i-1]);return ""+best;',4)

S=[sorted(a) for a in A]
aa('sorted-squared-values','Sorted Squared Values','The input array is nondecreasing. Print the squared values in nondecreasing order in linear time.',
   lambda a:sorted(x*x for x in a),
   'long[] b=new long[n];int l=0,r=n-1;for(int i=n-1;i>=0;i--){long x=(long)a[l]*a[l],y=(long)a[r]*a[r];if(x>y){b[i]=x;l++;}else{b[i]=y;r--;}}return join(b);',4,cases=S)

def wiggle(a):
    u=[1]*len(a); d=u.copy()
    for i in range(len(a)):
        for j in range(i):
            if a[i]>a[j]: u[i]=max(u[i],d[j]+1)
            if a[i]<a[j]: d[i]=max(d[i],u[j]+1)
    return max(u+d)
aa('longest-wiggle-subsequence','Longest Wiggle Subsequence','Print the longest subsequence length whose consecutive differences strictly alternate positive and negative. A single element qualifies; zero differences do not.',wiggle,
   'int up=1,down=1;for(int i=1;i<n;i++)if(a[i]>a[i-1])up=down+1;else if(a[i]<a[i-1])down=up+1;return ""+Math.max(up,down);',6)

aa('longest-zero-sum-segment','Longest Zero-Sum Segment','Print the length of the longest contiguous subarray summing to zero, or 0.',
   lambda a:max([j-i for i in range(len(a)) for j in range(i+1,len(a)+1) if sum(a[i:j])==0]+[0]),
   'Map<Long,Integer>first=new HashMap<>();first.put(0L,-1);long s=0;int best=0;for(int i=0;i<n;i++){s+=a[i];if(first.containsKey(s))best=Math.max(best,i-first.get(s));else first.put(s,i);}return ""+best;',6)

X=[(a,k) for a,k in zip(P,[0,2,0,7,1,3,8,9,12,100])]
add('count-target-xor-segments','Count Target XOR Segments',6,'Count nonempty contiguous subarrays whose bitwise XOR is k.',
    'n k, then n nonnegative values. 1 <= n <= 2000; 0 <= a[i], k <= 1000000.',X,arrk_input,
    lambda c:sum(__import__('functools').reduce(int.__xor__,c[0][i:j],0)==c[1] for i in range(len(c[0])) for j in range(i+1,len(c[0])+1)),
    READK+'Map<Integer,Long>f=new HashMap<>();f.put(0,1L);int s=0;long ans=0;for(int x:a){s^=x;ans+=f.getOrDefault(s^k,0L);f.put(s,f.getOrDefault(s,0L)+1);}return ""+ans;')

W=[(a,max(1,len(a)//3)) for a in A]
add('maximum-fixed-window-sum','Maximum Fixed Window Sum',4,'Print the largest sum among contiguous subarrays of exactly k values.',
    KLAYOUT+' 1 <= k <= n.',W,arrk_input,lambda c:max(sum(c[0][i:i+c[1]]) for i in range(len(c[0])-c[1]+1)),
    READK+'long s=0,best=Long.MIN_VALUE;for(int i=0;i<n;i++){s+=a[i];if(i>=k)s-=a[i-k];if(i>=k-1)best=Math.max(best,s);}return ""+best;')

add('distinct-values-per-window','Distinct Values per Window',5,'For every window of exactly k consecutive values, print its number of distinct values from left to right.',
    KLAYOUT+' 1 <= k <= n.',W,arrk_input,lambda c:[len(set(c[0][i:i+c[1]])) for i in range(len(c[0])-c[1]+1)],
    READK+'Map<Integer,Integer>f=new HashMap<>();List<Integer>b=new ArrayList<>();for(int i=0;i<n;i++){f.put(a[i],f.getOrDefault(a[i],0)+1);if(i>=k){int x=a[i-k],v=f.get(x)-1;if(v==0)f.remove(x);else f.put(x,v);}if(i>=k-1)b.add(f.size());}return join(b);')

aa('smaller-values-to-right','Smaller Values to the Right','For every index, print the number of strictly smaller values to its right.',
   lambda a:[sum(x<a[i] for x in a[i+1:]) for i in range(len(a))],
   'int[] b=a.clone(),ans=new int[n],bit=new int[n+1];Arrays.sort(b);for(int i=n-1;i>=0;i--){int l=0,r=n;while(l<r){int m=(l+r)/2;if(b[m]<a[i])l=m+1;else r=m;}for(int j=l;j>0;j-=j&-j)ans[i]+=bit[j];for(int j=l+1;j<=n;j+=j&-j)bit[j]++;}return join(ans);',7)

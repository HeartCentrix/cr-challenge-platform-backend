import heapq
from collections import Counter, OrderedDict
from itertools import combinations, permutations, product
from common import *

A=arrays(55)
P=arrays(56,True)
RA='int n=in.nextInt();int[]a=array(in,n);'
RK='int n=in.nextInt(),k=in.nextInt();int[]a=array(in,n);'

add('running-median-doubled','Running Median Doubled',7,'After each value arrives, print twice the median of all values seen so far. For an even count use the average of the two middle values, so every printed result is an integer.',
    ARRAY_LAYOUT,A,arr_input,
    lambda a:[(lambda b:b[(len(b)-1)//2]+b[len(b)//2])(sorted(a[:i])) for i in range(1,len(a)+1)],
    RA+'PriorityQueue<Integer>lo=new PriorityQueue<>(Comparator.reverseOrder()),hi=new PriorityQueue<>();List<Long>b=new ArrayList<>();for(int x:a){if(lo.isEmpty()||x<=lo.peek())lo.add(x);else hi.add(x);if(lo.size()>hi.size()+1)hi.add(lo.remove());if(hi.size()>lo.size())lo.add(hi.remove());b.add(lo.size()==hi.size()?(long)lo.peek()+hi.peek():2L*lo.peek());}return join(b);')

PAIRCASES=[(a if len(a)>1 else a+[3],1+i) for i,a in enumerate(A)]
PAIRCASES=[(a,min(k,len(a)*(len(a)-1)//2)) for a,k in PAIRCASES]
add('kth-smallest-pair-distance','Kth Smallest Pair Distance',7,'For every index pair i < j form |a[i]-a[j]|. Sort these distances with duplicates and print the kth smallest.',
    'n k, then n values. 2 <= n <= 2000; 1 <= k <= n*(n-1)/2; -1000000 <= a[i] <= 1000000.',PAIRCASES,arrk_input,
    lambda c:sorted(abs(x-y) for x,y in combinations(c[0],2))[c[1]-1],
    RK+'Arrays.sort(a);int l=0,r=a[n-1]-a[0];while(l<r){int mid=(l+r)/2,left=0;long count=0;for(int i=0;i<n;i++){while(a[i]-a[left]>mid)left++;count+=i-left;}if(count>=k)r=mid;else l=mid+1;}return ""+l;')

MT=[(3,3,5),(2,3,6),(1,1,1),(1,10,7),(10,1,3),(5,5,1),(5,5,25),(7,9,30),(20,15,180),(100,80,7000)]
add('kth-multiplication-table-value','Kth Multiplication Table Value',7,'The table has value i*j at row i and column j, using one-based indices. Print the kth smallest value across all cells, counting duplicates.',
    'rows cols k; 1 <= rows, cols <= 30000; 1 <= k <= rows*cols.',MT,numbers_input,
    lambda c:sorted(i*j for i in range(1,c[0]+1) for j in range(1,c[1]+1))[c[2]-1],
    'int h=in.nextInt(),w=in.nextInt(),k=in.nextInt();long l=1,r=(long)h*w;while(l<r){long mid=(l+r)/2,count=0;for(int i=1;i<=h;i++)count+=Math.min(w,mid/i);if(count>=k)r=mid;else l=mid+1;}return ""+l;')

SM=[[7,2,5,10,8],[1,2,3,4,5],[0],[7],[0,0,0],[2,2,2,2],[9,1,1,9],[1,10,1,10,1],[5,4,3,2,1],[3,1,4,1,5,9,2,6,5]]
SPLIT=[(a,max(1,len(a)//2)) for a in SM]
def split(c):
    a,k=c
    return min(max(sum(a[x:y]) for x,y in zip((0,)+cuts,cuts+(len(a),))) for cuts in combinations(range(1,len(a)),k-1))
add('minimize-largest-partition-sum','Minimize Largest Partition Sum',7,'Split the array into exactly k nonempty contiguous parts. Print the minimum possible value of the largest part sum.',
    'n k, then n nonnegative values. 1 <= k <= n <= 2000; 0 <= a[i] <= 1000000.',SPLIT,arrk_input,split,
    RK+'long l=0,r=0;for(int x:a){l=Math.max(l,x);r+=x;}while(l<r){long mid=(l+r)/2,sum=0;int parts=1;for(int x:a){if(sum+x>mid){parts++;sum=0;}sum+=x;}if(parts<=k)r=mid;else l=mid+1;}return ""+l;')

BAN=[([3,6,7,11],8),([30,11,23,4,20],5),([1],1),([100],10),([2,2],4),([9,9,9],4),([1,1,1],10),([5,10,15],8),([17,19,23,29],17),([i*7+1 for i in range(15)],60)]
add('minimum-pile-eating-speed','Minimum Pile Eating Speed',6,'Each hour process up to speed items from one pile; unused capacity that hour cannot be used on another pile. Print minimum positive integer speed that finishes all piles within h hours.',
    'n h, then n positive pile sizes. 1 <= n <= h <= 1000000000; n <= 2000; 1 <= pile <= 1000000.',BAN,arrk_input,
    lambda c:next(s for s in range(1,max(c[0])+1) if sum((x+s-1)//s for x in c[0])<=c[1]),
    RK+'int l=1,r=0;for(int x:a)r=Math.max(r,x);while(l<r){int mid=(l+r)/2;long hours=0;for(int x:a)hours+=(x+mid-1)/mid;if(hours<=k)r=mid;else l=mid+1;}return ""+l;')

COW=[([1,2,8,4,9],3),([1,2,3,4,5],2),([0,1],2),([0,100],2),([1,2,3],3),([2,5,11,17],3),([0,10,20,30],4),([3,8,15,21,34],3),([0,1,2,100,101,102],3),([i*i for i in range(12)],5)]
add('maximize-minimum-placement-gap','Maximize Minimum Placement Gap',7,'Choose k distinct positions from the given distinct coordinates. Print the maximum possible minimum distance between consecutive chosen coordinates in sorted order.',
    'n k, then n distinct coordinates. 2 <= k <= n <= 2000; 0 <= coordinate <= 1000000000.',COW,arrk_input,
    lambda c:max(min(y-x for x,y in zip(sorted(t),sorted(t)[1:])) for t in combinations(c[0],c[1])),
    RK+'Arrays.sort(a);int l=0,r=a[n-1]-a[0];while(l<r){int mid=l+(r-l+1)/2,count=1,last=a[0];for(int x:a)if(x-last>=mid){count++;last=x;}if(count>=k)l=mid;else r=mid-1;}return ""+l;')

INT=[[(0,30),(5,10),(15,20)],[(1,2),(2,3)],[(0,1)],[(1,3),(1,3)],[(1,10),(2,9),(3,8)],[(i,i+1) for i in range(6)],[(0,2),(1,3),(2,4),(3,5)],[(0,100),(10,20),(20,30)],[(i%5,i%5+2) for i in range(9)],[(i*3,i*3+7) for i in range(15)]]
def records(a):return str(len(a))+'\n'+''.join(' '.join(map(str,row))+'\n' for row in a)
IL='n, then n lines start end. 1 <= n <= 2000; 0 <= start < end <= 1000000. Intervals are half-open [start,end).'
RI='int n=in.nextInt();int[][]a=new int[n][2];for(int[]r:a){r[0]=in.nextInt();r[1]=in.nextInt();}'
add('minimum-meeting-rooms','Minimum Meeting Rooms',6,'Print the minimum simultaneous rooms needed for all meetings. An ending meeting frees its room for another starting at the same time.',
    IL,INT,records,lambda a:max(sum(s<=t<e for s,e in a) for t in {s for s,e in a}),
    RI+'Arrays.sort(a,Comparator.comparingInt(x->x[0]));PriorityQueue<Integer>q=new PriorityQueue<>();int best=0;for(int[]r:a){while(!q.isEmpty()&&q.peek()<=r[0])q.remove();q.add(r[1]);best=Math.max(best,q.size());}return ""+best;')

def compatible(a):
    return max(len(t) for k in range(len(a)+1) for t in combinations(a,k) if all(x[1]<=y[0] or y[1]<=x[0] for x,y in combinations(t,2)))
add('maximum-compatible-meetings','Maximum Compatible Meetings',5,'Choose the maximum number of nonoverlapping meetings for one room. Print that number; touching endpoints are compatible.',
    IL,INT,records,compatible,
    RI+'Arrays.sort(a,Comparator.comparingInt(x->x[1]));int end=-1,ans=0;for(int[]r:a)if(r[0]>=end){ans++;end=r[1];}return ""+ans;')

INTER=[([(0,2),(5,10),(13,23)],[(1,5),(8,12),(15,24)]), ([(0,1)],[(1,2)]), ([],[]), ([],[(1,2)]), ([(1,2)],[]), ([(1,5)],[(2,3)]), ([(0,10)],[(0,10)]), ([(0,2),(4,6)],[(2,4)]), ([(i*4,i*4+2) for i in range(6)],[(i*3,i*3+1) for i in range(8)]), ([(0,100)],[(i*3,i*3+1) for i in range(20)])]
def two_records(c):return records(c[0])+records(c[1])
def intersects(c):
    out=sorted((max(x[0],y[0]),min(x[1],y[1])) for x in c[0] for y in c[1] if max(x[0],y[0])<=min(x[1],y[1]))
    return '\n'.join(f'{x} {y}' for x,y in out) or 'EMPTY'
add('closed-interval-intersections','Closed Interval Intersections',6,'Each list contains sorted, pairwise disjoint closed intervals. Print their intersections in ascending order, one start end per line, including single-point intersections. Print EMPTY if none.',
    'n then n start end pairs, followed by m then m start end pairs. 0 <= n,m <= 2000; 0 <= start <= end <= 1000000. Within a list, each end is strictly less than the next start.',INTER,two_records,intersects,
    RI+r'int m=in.nextInt();int[][]b=new int[m][2];for(int[]r:b){r[0]=in.nextInt();r[1]=in.nextInt();}List<String>out=new ArrayList<>();int i=0,j=0;while(i<n&&j<m){int l=Math.max(a[i][0],b[j][0]),r=Math.min(a[i][1],b[j][1]);if(l<=r)out.add(l+" "+r);if(a[i][1]<b[j][1])i++;else j++;}return out.isEmpty()?"EMPTY":String.join("\n",out);')

LISTS=[[[1,4,5],[1,3,4],[2,6]],[[1],[0]], [[]], [[7]], [[],[]], [[1,1],[1,1]], [[-4,-2],[0,2],[]], [[1,3,9],[-1,5],[2,7,8]], [list(range(i,20,4)) for i in range(4)], [sorted(a) for a in A[:5]]]
def list_input(a):return str(len(a))+'\n'+''.join(arr_input(r) for r in a)
add('merge-k-sorted-sequences','Merge K Sorted Sequences',6,'Merge all sequences into one nondecreasing sequence, retaining duplicates. Print EMPTY if all are empty.',
    'k, followed by k sequences, each encoded as its length and then its values. 1 <= k <= 100; total values <= 10000; each sequence is sorted; values are signed 32-bit integers.',LISTS,list_input,
    lambda a:sorted(x for row in a for x in row),
    'int k=in.nextInt();int[][]a=new int[k][];PriorityQueue<int[]>q=new PriorityQueue<>((x,y)->Integer.compare(a[x[0]][x[1]],a[y[0]][y[1]]));for(int i=0;i<k;i++){int n=in.nextInt();a[i]=array(in,n);if(n>0)q.add(new int[]{i,0});}List<Integer>b=new ArrayList<>();while(!q.isEmpty()){int[]u=q.remove();b.add(a[u[0]][u[1]]);u[1]++;if(u[1]<a[u[0]].length)q.add(u);}return join(b);')

ROPE=[[4,3,2,6],[1,2,3],[7],[0],[0,0,0],[1,1,1,1],[2,5,8,9],[10,20,30,40],[1,2,4,8,16],[9,7,5,3,1,2]]
def rope(a):
    from functools import lru_cache
    @lru_cache(None)
    def f(t):
        if len(t)<=1:return 0
        return min(t[i]+t[j]+f(tuple(sorted([v for k,v in enumerate(t) if k not in (i,j)]+[t[i]+t[j]]))) for i in range(len(t)) for j in range(i+1,len(t)))
    return f(tuple(sorted(a)))
add('minimum-rope-connection-cost','Minimum Rope Connection Cost',6,'Join all ropes into one. Each join costs the sum of the two joined lengths and creates a rope of that length. Print minimum total cost; one rope costs 0.',
    POS_ARRAY_LAYOUT,ROPE,arr_input,rope,
    RA+'PriorityQueue<Long>q=new PriorityQueue<>();for(int x:a)q.add((long)x);long cost=0;while(q.size()>1){long s=q.remove()+q.remove();cost+=s;q.add(s);}return ""+cost;')

TASK=[('aaabbb','2'),('aaabbb','0'),('a','5'),('aaaa','3'),('abcd','2'),('aaabbc','2'),('aabbcc','1'),('aaabbbccc','3'),('aaaabbbccdd','2'),('abcdefghij','9')]
def tasktime(c):
    s,k=c;k=int(k);counts=Counter(s)
    # Simulate a work-conserving schedule, preferring the most frequent eligible task.
    ready=[];cool=[];time=0
    for x,n in counts.items():heapq.heappush(ready,(-n,x))
    while ready or cool:
        while cool and cool[0][0]<=time:
            _,n,x=heapq.heappop(cool);heapq.heappush(ready,(n,x))
        if ready:
            n,x=heapq.heappop(ready)
            if n+1<0:heapq.heappush(cool,(time+k+1,n+1,x))
        time+=1
    return time
add('task-scheduler-cooldown','Task Scheduler Cooldown',7,'Each character is a unit-time task. Reorder tasks freely; equal letters require at least k intervening time slots. Idle slots are allowed. Print minimum total slots.',
    'A lowercase string of 1 to 10000 tasks, followed by cooldown k (0 <= k <= 1000).',TASK,words_input,tasktime,
    'String s=in.next();int k=in.nextInt(),mx=0,tied=0;int[]f=new int[26];for(char c:s.toCharArray())f[c-97]++;for(int x:f)mx=Math.max(mx,x);for(int x:f)if(x==mx)tied++;return ""+Math.max(s.length(),(mx-1)*(k+1)+tied);')

GAS=[[(1,3),(2,4),(3,5),(4,1),(5,2)],[(2,3),(3,4),(4,3)],[(0,0)],[(1,2)],[(3,1)],[(1,1),(1,1)],[(0,2),(3,1),(0,0)],[(5,1),(1,4),(2,3)],[(i%3+1,(i+1)%3+1) for i in range(6)],[(0,4),(0,3),(10,1),(1,1)]]
def gas(a):
    for start in range(len(a)):
        tank=0
        for j in range(len(a)):
            x,y=a[(start+j)%len(a)];tank+=x-y
            if tank<0:break
        else:return start
    return -1
add('circular-fuel-route','Circular Fuel Route',6,'Stations form a circle. At each station collect gas then spend cost to reach the next. Start with an empty tank of unlimited capacity. Print the smallest starting index that completes one circuit, or -1.',
    'n, then n lines gas cost. 1 <= n <= 2000; 0 <= gas,cost <= 1000000.',GAS,records,gas,
    RI+'long tank=0,total=0;int start=0;for(int i=0;i<n;i++){long diff=(long)a[i][0]-a[i][1];tank+=diff;total+=diff;if(tank<0){tank=0;start=i+1;}}return ""+(total<0?-1:start%n);')

def candy(a):
    c=[1]*len(a)
    while True:
        changed=False
        for i in range(len(a)):
            for j in (i-1,i+1):
                if 0<=j<len(a) and a[i]>a[j] and c[i]<=c[j]:c[i]=c[j]+1;changed=True
        if not changed:return sum(c)
add('minimum-rating-candies','Minimum Rating Candies',6,'Give each child at least one candy. A child with a strictly higher rating than an immediate neighbor must receive more candies than that neighbor. Print the minimum total.',
    ARRAY_LAYOUT,A,arr_input,candy,
    RA+'int[]c=new int[n];Arrays.fill(c,1);for(int i=1;i<n;i++)if(a[i]>a[i-1])c[i]=c[i-1]+1;for(int i=n-2;i>=0;i--)if(a[i]>a[i+1])c[i]=Math.max(c[i],c[i+1]+1);long sum=0;for(int x:c)sum+=x;return ""+sum;')

HEIGHTS=[[7,4,7,5,6,5],[1,2,3],[1],[8],[3,3,3],[9,8,7,6],[2,5,2,6],[4,1,5,2,6],[1,3,2,4,3],[i%5+1 for i in range(12)]]
QUEUE=[]
for hs in HEIGHTS:
    pairs=[(h,sum(x>=h for x in hs[:i])) for i,h in enumerate(hs)]
    random.Random(sum(hs)).shuffle(pairs);QUEUE.append(pairs)
def queue_solution(a):
    q=[]
    for h,k in sorted(a,key=lambda x:(-x[0],x[1])):q.insert(k,(h,k))
    return [h for h,k in q]
add('queue-height-reconstruction','Queue Height Reconstruction',7,'Each record gives a height and the number of people at least that tall standing before that person. Reconstruct the queue and print its heights in order. Inputs describe a valid, uniquely determined height sequence.',
    'n, then n lines height count. 1 <= n <= 2000; 1 <= height <= 1000000; 0 <= count < n.',QUEUE,records,queue_solution,
    RI+'Arrays.sort(a,(x,y)->x[0]==y[0]?Integer.compare(x[1],y[1]):Integer.compare(y[0],x[0]));List<Integer>q=new ArrayList<>();for(int[]r:a)q.add(r[1],r[0]);return join(q);')

BUILD=[[(2,9,10),(3,7,15),(5,12,12),(15,20,10),(19,24,8)],[(0,2,3),(2,5,3)],[(0,1,1)],[(1,3,2),(1,3,5)],[(1,10,3),(2,3,8)],[(i*3,i*3+2,i+1) for i in range(5)],[(0,5,1),(1,4,2),(2,3,3)],[(0,3,3),(1,4,3),(2,5,3)],[(i,i+4,i%3+2) for i in range(7)],[(0,100,1),(20,40,5),(60,80,4)]]
def skyline(a):
    out=[];last=0
    for x in sorted({x for l,r,h in a for x in (l,r)}):
        height=max([h for l,r,h in a if l<=x<r]+[0])
        if height!=last:out.append((x,height));last=height
    return '\n'.join(f'{x} {y}' for x,y in out)
add('building-skyline','Building Skyline',8,'Buildings occupy [left,right) at a fixed height. Print skyline key points x height in increasing x order whenever the visible maximum height changes, ending with height 0. Omit redundant consecutive equal heights.',
    'n, then n lines left right height. 1 <= n <= 2000; 0 <= left < right <= 1000000; 1 <= height <= 1000000.',BUILD,records,skyline,
    r'int n=in.nextInt();List<int[]>events=new ArrayList<>();for(int i=0;i<n;i++){int l=in.nextInt(),r=in.nextInt(),h=in.nextInt();events.add(new int[]{l,h,1});events.add(new int[]{r,h,-1});}events.sort(Comparator.comparingInt(x->x[0]));TreeMap<Integer,Integer>active=new TreeMap<>();active.put(0,1);List<String>b=new ArrayList<>();int prev=0;for(int i=0;i<events.size();){int x=events.get(i)[0];while(i<events.size()&&events.get(i)[0]==x){int[]e=events.get(i++);int count=active.getOrDefault(e[1],0)+e[2];if(count==0)active.remove(e[1]);else active.put(e[1],count);}int h=active.lastKey();if(h!=prev){b.add(x+" "+h);prev=h;}}return String.join("\n",b);')

PTS=[[(0,0),(2,0),(2,2),(0,2),(1,1)],[(0,0),(1,1),(2,2)],[(0,0)],[(2,3),(5,7)],[(0,0),(1,0),(0,1)],[(0,0),(0,0),(1,1)],[(i,i*i) for i in range(6)],[(i%3,i//3) for i in range(9)],[(-3,-2),(-1,5),(4,4),(5,-1),(0,0)],[(random.Random(i).randint(-20,20),random.Random(i+90).randint(-20,20)) for i in range(20)]]
def hull(a):
    points=sorted(set(a))
    if len(points)<3:return 0
    # Gift wrapping, independent of the monotone-chain Java implementation.
    start=points[0];p=start;out=[]
    while True:
        out.append(p);q=next(x for x in points if x!=p)
        for r in points:
            cross=(q[0]-p[0])*(r[1]-p[1])-(q[1]-p[1])*(r[0]-p[0])
            if cross<0 or (cross==0 and sum((r[i]-p[i])**2 for i in (0,1))>sum((q[i]-p[i])**2 for i in (0,1))):q=r
        p=q
        if p==start:break
    return abs(sum(x[0]*y[1]-x[1]*y[0] for x,y in zip(out,out[1:]+out[:1])))
add('convex-hull-doubled-area','Convex Hull Doubled Area',8,'Print twice the area of the convex hull of the points. Duplicate points are allowed. Fewer than three noncollinear distinct points gives 0.',
    'n, then n lines x y. 1 <= n <= 2000; -1000000 <= x,y <= 1000000.',PTS,records,hull,
    RI+'Arrays.sort(a,(x,y)->x[0]==y[0]?Integer.compare(x[1],y[1]):Integer.compare(x[0],y[0]));List<int[]>hull=new ArrayList<>();for(int pass=0;pass<2;pass++){int base=hull.size();for(int index=0;index<n;index++){int[]p=a[pass==0?index:n-1-index];while(hull.size()>=base+2){int[]u=hull.get(hull.size()-2),v=hull.get(hull.size()-1);long cross=(long)(v[0]-u[0])*(p[1]-u[1])-(long)(v[1]-u[1])*(p[0]-u[0]);if(cross>0)break;hull.remove(hull.size()-1);}hull.add(p);}hull.remove(hull.size()-1);}long area=0;for(int i=0;i<hull.size();i++){int[]u=hull.get(i),v=hull.get((i+1)%hull.size());area+=(long)u[0]*v[1]-(long)u[1]*v[0];}return ""+Math.abs(area);')

CP=[a if len(a)>1 else a+[(3,4)] for a in PTS]
add('closest-point-pair-squared-distance','Closest Point Pair Squared Distance',6,'Print the smallest squared Euclidean distance between two different point indices. Duplicate points therefore give 0.',
    'n, then n lines x y. 2 <= n <= 2000; -1000000 <= x,y <= 1000000.',CP,records,
    lambda a:min((x[0]-y[0])**2+(x[1]-y[1])**2 for x,y in combinations(a,2)),
    RI+'long best=Long.MAX_VALUE;for(int i=0;i<n;i++)for(int j=i+1;j<n;j++){long x=(long)a[i][0]-a[j][0],y=(long)a[i][1]-a[j][1];best=Math.min(best,x*x+y*y);}return ""+best;')

RECT=[[(0,0,2,2),(1,1,3,3)],[(0,0,1,1),(2,2,3,3)],[(0,0,1,1)],[(0,0,2,2),(0,0,2,2)],[(0,0,10,10),(2,2,3,3)],[(0,0,2,2),(2,0,4,2)],[(i,i,i+3,i+2) for i in range(5)],[(-2,-3,2,3),(0,0,5,4)],[(0,0,10,1),(0,0,1,10)],[(i%3,i%4,i%3+2,i%4+2) for i in range(12)]]
def union_area(a):return len({(x,y) for l,b,r,t in a for x in range(l,r) for y in range(b,t)})
add('rectangle-union-area','Rectangle Union Area',8,'Print the area covered by at least one axis-aligned rectangle, counting overlapping regions only once.',
    'n, then n lines left bottom right top. 1 <= n <= 100; -1000000 <= left < right <= 1000000; -1000000 <= bottom < top <= 1000000.',RECT,records,union_area,
    'int n=in.nextInt();int[][]a=new int[n][4];TreeSet<Integer>xs=new TreeSet<>();for(int[]r:a){for(int j=0;j<4;j++)r[j]=in.nextInt();xs.add(r[0]);xs.add(r[2]);}List<Integer>x=new ArrayList<>(xs);long ans=0;for(int k=1;k<x.size();k++){int left=x.get(k-1),right=x.get(k);List<int[]>segments=new ArrayList<>();for(int[]r:a)if(r[0]<=left&&r[2]>=right)segments.add(new int[]{r[1],r[3]});segments.sort(Comparator.comparingInt(r->r[0]));long covered=0;int end=Integer.MIN_VALUE;for(int[]r:segments){if(r[1]>end){covered+=(long)r[1]-Math.max(end,r[0]);end=r[1];}}ans+=covered*(right-left);}return ""+ans;')

OPS=[]
for i,a in enumerate(A):
    n=len(a);ops=[('SUM',0,n-1),('ADD',i%n,7-i),('SUM',0,i%n),('ADD',0,-3),('SUM',0,n-1),('SUM',n-1,n-1)]
    OPS.append((a,ops))
def op_input(c):
    a,ops=c
    return f'{len(a)} {len(ops)}\n'+' '.join(map(str,a))+'\n'+''.join(' '.join(map(str,o))+'\n' for o in ops)
def op_oracle(c):
    a,ops=c;a=a[:];ans=[]
    for cmd,x,y in ops:
        if cmd=='ADD':a[x]+=y
        else:ans.append(sum(a[x:y+1]))
    return ans
add('dynamic-range-sums','Dynamic Range Sums',7,'Process ADD index delta, which increments one value, and SUM left right, which queries an inclusive range. Print the SUM answers in command order, separated by spaces.',
    'n q, then n values, then q commands. 1 <= n,q <= 100000; -1000000 <= value,delta <= 1000000; all indices valid; left <= right. At least one SUM occurs.',OPS,op_input,op_oracle,
    'int n=in.nextInt(),q=in.nextInt();long[]bit=new long[n+1];for(int i=0;i<n;i++){long x=in.nextLong();for(int j=i+1;j<=n;j+=j&-j)bit[j]+=x;}List<Long>ans=new ArrayList<>();while(q-->0){String cmd=in.next();int x=in.nextInt(),y=in.nextInt();if(cmd.equals("ADD")){for(int j=x+1;j<=n;j+=j&-j)bit[j]+=y;}else{long s=0;for(int j=y+1;j>0;j-=j&-j)s+=bit[j];for(int j=x;j>0;j-=j&-j)s-=bit[j];ans.add(s);}}return join(ans);')

LRU=[]
for cap in range(1,11):
    ops=[('GET',1),('PUT',1,10),('PUT',2,20),('GET',1),('PUT',3,30),('GET',2),('PUT',1,99),('GET',1)]
    ops += [('PUT',i,i*5) for i in range(4,cap+5)]+[('GET',3),('GET',cap+4)]
    LRU.append((cap,ops))
def cache_input(c):return f'{c[0]} {len(c[1])}\n'+''.join(' '.join(map(str,o))+'\n' for o in c[1])
def cache(c):
    cap,ops=c;order=[];values={};out=[]
    for op in ops:
        cmd,key=op[:2]
        if cmd=='GET':out.append(values.get(key,-1))
        if cmd=='PUT' or key in values:
            if key in order:order.remove(key)
            order.append(key)
        if cmd=='PUT':values[key]=op[2]
        if len(order)>cap:del values[order.pop(0)]
    return out
add('least-recently-used-cache','Least Recently Used Cache',7,'Simulate an LRU cache. GET key prints its value or -1 if missing; successful GET and every PUT make the key most recently used. PUT key value updates/inserts and evicts the least recently used key if needed. Print GET results in order, separated by spaces.',
    'capacity q, then q commands GET key or PUT key value. 1 <= capacity,q <= 100000; 0 <= key,value <= 1000000. At least one GET occurs.',LRU,cache_input,cache,
    'int cap=in.nextInt(),q=in.nextInt();LinkedHashMap<Integer,Integer>cache=new LinkedHashMap<>(16,0.75f,true);List<Integer>out=new ArrayList<>();while(q-->0){String cmd=in.next();int key=in.nextInt();if(cmd.equals("GET"))out.add(cache.getOrDefault(key,-1));else{int value=in.nextInt();cache.put(key,value);if(cache.size()>cap){Integer first=cache.keySet().iterator().next();cache.remove(first);}}}return join(out);')

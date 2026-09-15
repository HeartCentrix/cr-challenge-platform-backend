from itertools import combinations, permutations, product
from common import *

G=[(5,[(0,1),(1,2),(3,4)]),(3,[(0,1),(1,2),(2,0)]),(1,[]),(4,[]),
   (4,[(0,1),(1,2),(2,3)]),(4,[(0,1),(0,2),(0,3)]),
   (5,[(0,1),(1,2),(2,0),(2,3),(3,4)]),(6,[(0,1),(1,2),(2,3),(3,0),(4,5)]),
   (6,list(combinations(range(6),2))),(9,[(i,i+1) for i in range(8)]+[(0,4),(2,7)])]
D=[(4,[(0,1),(0,2),(1,3),(2,3)]),(3,[(0,1),(1,2),(2,0)]),(1,[]),(4,[]),
   (5,[(0,1),(1,2),(2,3),(3,4)]),(4,[(1,0),(2,0),(3,0)]),
   (5,[(0,1),(1,0),(1,2),(2,3),(3,2),(3,4)]),(6,[(0,1),(1,2),(2,1),(3,4),(4,5)]),
   (6,[(i,j) for i in range(6) for j in range(i+1,6)]),(8,[(i,(i+1)%8) for i in range(8)])]
GL='n m, followed by m lines u v. Vertices are 0 through n-1. The graph is simple and undirected (no loops or duplicate edges). 1 <= n <= 80; 0 <= m <= 500.'
DL=GL.replace('undirected','directed')

def encode(c):
    n,e=c
    return f'{n} {len(e)}\n'+''.join(' '.join(map(str,row))+'\n' for row in e)

def matrix(c,directed=False):
    n,e=c; a=[[10**12]*n for _ in range(n)]
    for i in range(n): a[i][i]=0
    for row in e:
        u,v=row[:2]; w=row[2] if len(row)==3 else 1
        a[u][v]=w
        if not directed: a[v][u]=w
    for k in range(n):
        for i in range(n):
            for j in range(n): a[i][j]=min(a[i][j],a[i][k]+a[k][j])
    return a

def components(c,skip=-1):
    n,e=c; groups=[]; todo=set(range(n))-{skip}
    while todo:
        q={todo.pop()}; group=set(q)
        while q:
            x=q.pop()
            for u,v in e:
                y=v if u==x else u if v==x else -1
                if y in todo: todo.remove(y);group.add(y);q.add(y)
        groups.append(group)
    return groups

def read(directed=False,weighted=False):
    return ('int n=in.nextInt(),m=in.nextInt();int[][]a=new int[n][n];int[][]edges=new int[m][3];'
            'for(int i=0;i<m;i++){int u=in.nextInt(),v=in.nextInt(),w='+('in.nextInt()' if weighted else '1')+';'
            'edges[i]=new int[]{u,v,w};a[u][v]=w;'+('' if directed else 'a[v][u]=w;')+'}')

FLOYD='long INF=1000000000000L;long[][]d=new long[n][n];for(int i=0;i<n;i++)for(int j=0;j<n;j++)d[i][j]=i==j?0:a[i][j]==0?INF:a[i][j];for(int k=0;k<n;k++)for(int i=0;i<n;i++)for(int j=0;j<n;j++)d[i][j]=Math.min(d[i][j],d[i][k]+d[k][j]);'
COMP='boolean[]seen=new boolean[n];int count=0;for(int start=0;start<n;start++)if(!seen[start]){count++;Deque<Integer>q=new ArrayDeque<>();q.add(start);seen[start]=true;while(!q.isEmpty()){int u=q.remove();for(int v=0;v<n;v++)if(a[u][v]!=0&&!seen[v]){seen[v]=true;q.add(v);}}}'

def graph(slug,title,task,oracle,java,diff=6,cases=G,directed=False,weighted=False,layout=None):
    add(slug,title,diff,task,layout or (DL if directed else GL),cases,encode,oracle,read(directed,weighted)+java)

graph('undirected-component-count','Undirected Component Count','Print the number of connected components, including isolated vertices.',lambda c:len(components(c)),COMP+'return ""+count;',5)
graph('source-reachable-count','Source Reachable Count','Print the number of vertices reachable from vertex 0, including vertex 0.',lambda c:sum(x<10**12 for x in matrix(c,True)[0]),
      'boolean[]seen=new boolean[n];Deque<Integer>q=new ArrayDeque<>();q.add(0);seen[0]=true;int ans=0;while(!q.isEmpty()){int u=q.remove();ans++;for(int v=0;v<n;v++)if(a[u][v]!=0&&!seen[v]){seen[v]=true;q.add(v);}}return ""+ans;',5,cases=D,directed=True)
graph('unweighted-source-distances','Unweighted Source Distances','Print the shortest edge distance from vertex 0 to every vertex in order. Use -1 for unreachable vertices.',
      lambda c:[x if x<10**12 else -1 for x in matrix(c)[0]],
      'int[]d=new int[n];Arrays.fill(d,-1);d[0]=0;Deque<Integer>q=new ArrayDeque<>();q.add(0);while(!q.isEmpty()){int u=q.remove();for(int v=0;v<n;v++)if(a[u][v]!=0&&d[v]<0){d[v]=d[u]+1;q.add(v);}}return join(d);',5)
graph('bipartite-graph-check','Bipartite Graph Check','Print true if every edge can connect vertices of different colors using two colors, otherwise false.',
      lambda c:any(all(colors[u]!=colors[v] for u,v in c[1]) for colors in product(range(2),repeat=c[0])),
      'int[]color=new int[n];for(int s=0;s<n;s++)if(color[s]==0){Deque<Integer>q=new ArrayDeque<>();q.add(s);color[s]=1;while(!q.isEmpty()){int u=q.remove();for(int v=0;v<n;v++)if(a[u][v]!=0){if(color[v]==color[u])return "false";if(color[v]==0){color[v]=-color[u];q.add(v);}}}}return "true";')
graph('undirected-cycle-check','Undirected Cycle Check','Print true if the graph contains a cycle, otherwise false.',
      lambda c:len(c[1])>c[0]-len(components(c)),
      'int[]p=new int[n];for(int i=0;i<n;i++)p[i]=i;for(int[]e:edges){int u=e[0],v=e[1];while(u!=p[u])u=p[u];while(v!=p[v])v=p[v];if(u==v)return "true";p[u]=v;}return "false";')

graph('bridge-edge-count','Bridge Edge Count','Print the number of edges whose removal increases the connected-component count.',
      lambda c:sum(len(components((c[0],c[1][:i]+c[1][i+1:])))>len(components(c)) for i in range(len(c[1]))),
      'int ans=0;for(int[]e:edges){int s=e[0],t=e[1];a[s][t]=a[t][s]=0;boolean[]seen=new boolean[n];Deque<Integer>q=new ArrayDeque<>();q.add(s);seen[s]=true;while(!q.isEmpty()){int u=q.remove();for(int v=0;v<n;v++)if(a[u][v]!=0&&!seen[v]){seen[v]=true;q.add(v);}}if(!seen[t])ans++;a[s][t]=a[t][s]=1;}return ""+ans;',7)

graph('articulation-vertex-count','Articulation Vertex Count','Print the number of vertices whose removal, together with incident edges, increases the number of connected components.',
      lambda c:sum(len(components(c,i))>len(components(c)) for i in range(c[0])),
      COMP+'int base=count,ans=0;for(int removed=0;removed<n;removed++){Arrays.fill(seen,false);seen[removed]=true;int cc=0;for(int s=0;s<n;s++)if(!seen[s]){cc++;Deque<Integer>q=new ArrayDeque<>();q.add(s);seen[s]=true;while(!q.isEmpty()){int u=q.remove();for(int v=0;v<n;v++)if(a[u][v]!=0&&!seen[v]){seen[v]=true;q.add(v);}}}if(cc>base)ans++;}return ""+ans;',7)

def topo(c):
    n,e=c
    return next((' '.join(map(str,p)) for p in permutations(range(n)) if all(p.index(u)<p.index(v) for u,v in e)),'IMPOSSIBLE')
graph('lexicographic-topological-order','Lexicographic Topological Order','Print the lexicographically smallest topological ordering of all vertices, or IMPOSSIBLE if a cycle exists.',topo,
      'int[]deg=new int[n];for(int[]e:edges)deg[e[1]]++;PriorityQueue<Integer>q=new PriorityQueue<>();for(int i=0;i<n;i++)if(deg[i]==0)q.add(i);List<Integer>b=new ArrayList<>();while(!q.isEmpty()){int u=q.remove();b.add(u);for(int v=0;v<n;v++)if(a[u][v]!=0&&--deg[v]==0)q.add(v);}return b.size()==n?join(b):"IMPOSSIBLE";',6,cases=D,directed=True)

def scc(c):
    a=matrix(c,True);seen=set();ans=0
    for i in range(c[0]):
        if i not in seen:
            ans+=1;seen.update(j for j in range(c[0]) if a[i][j]<10**12 and a[j][i]<10**12)
    return ans
graph('strongly-connected-component-count','Strongly Connected Component Count','Print the number of maximal groups in which every vertex can reach every other vertex.',scc,
      FLOYD+'boolean[]seen=new boolean[n];int ans=0;for(int i=0;i<n;i++)if(!seen[i]){ans++;for(int j=0;j<n;j++)if(d[i][j]<INF&&d[j][i]<INF)seen[j]=true;}return ""+ans;',7,cases=D,directed=True)

graph('universal-source-vertices','Universal Source Vertices','Print all vertices from which every vertex is reachable, in ascending order. Print EMPTY if none.',
      lambda c:[i for i,row in enumerate(matrix(c,True)) if all(x<10**12 for x in row)],
      FLOYD+'List<Integer>b=new ArrayList<>();for(int i=0;i<n;i++){boolean ok=true;for(int j=0;j<n;j++)if(d[i][j]>=INF)ok=false;if(ok)b.add(i);}return join(b);',6,cases=D,directed=True)

def safe(c):
    n,e=c;d=matrix(c,True)
    cyclic={u for u,v in e if d[v][u]<10**12}
    return [i for i in range(n) if all(d[i][j]>=10**12 for j in cyclic)]
graph('eventually-safe-vertices','Eventually Safe Vertices','A vertex is safe if every possible directed walk starting there eventually stops at a vertex with no outgoing edges. Print all safe vertices in ascending order, or EMPTY.',safe,
      'int[]out=new int[n];Deque<Integer>q=new ArrayDeque<>();for(int[]e:edges)out[e[0]]++;for(int i=0;i<n;i++)if(out[i]==0)q.add(i);boolean[]ok=new boolean[n];while(!q.isEmpty()){int u=q.remove();ok[u]=true;for(int v=0;v<n;v++)if(a[v][u]!=0&&--out[v]==0)q.add(v);}List<Integer>b=new ArrayList<>();for(int i=0;i<n;i++)if(ok[i])b.add(i);return join(b);',7,cases=D,directed=True)

graph('undirected-triangle-count','Undirected Triangle Count','Print the number of distinct sets of three vertices with all three connecting edges present.',
      lambda c:sum(all((min(u,v),max(u,v)) in {tuple(sorted(e)) for e in c[1]} for u,v in combinations(t,2)) for t in combinations(range(c[0]),3)),
      'long ans=0;for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)if(a[i][j]!=0)for(int k=j+1;k<n;k++)if(a[i][k]!=0&&a[j][k]!=0)ans++;return ""+ans;',5)

def euler(c):
    n,e=c;degree=[sum(i in edge for edge in e) for i in range(n)]
    return sum(d%2 for d in degree) in (0,2) and sum(any(degree[i] for i in g) for g in components(c))<=1
graph('euler-trail-existence','Euler Trail Existence','Print true if a walk can use every edge exactly once. Vertices may repeat; isolated vertices can be ignored. A graph with no edges qualifies.',euler,
      'int[]deg=new int[n];for(int[]e:edges){deg[e[0]]++;deg[e[1]]++;}int odd=0,start=-1;for(int i=0;i<n;i++){odd+=deg[i]%2;if(deg[i]>0)start=i;}if(start<0)return "true";if(odd!=0&&odd!=2)return "false";boolean[]seen=new boolean[n];Deque<Integer>q=new ArrayDeque<>();q.add(start);seen[start]=true;while(!q.isEmpty()){int u=q.remove();for(int v=0;v<n;v++)if(a[u][v]!=0&&!seen[v]){seen[v]=true;q.add(v);}}for(int i=0;i<n;i++)if(deg[i]>0&&!seen[i])return "false";return "true";',6)

graph('hamiltonian-path-count','Hamiltonian Path Count','Count directed paths that start at 0, end at n-1, and visit every vertex exactly once, modulo 1000000007. For n=1 the zero-edge path counts once.',
      lambda c:sum(all((u,v) in c[1] for u,v in zip(p,p[1:])) for p in permutations(range(c[0])) if p[0]==0 and p[-1]==c[0]-1),
      'long[][]dp=new long[1<<n][n];dp[1][0]=1;for(int mask=1;mask<(1<<n);mask++)for(int u=0;u<n;u++)if(dp[mask][u]!=0)for(int v=0;v<n;v++)if(a[u][v]!=0&&(mask&(1<<v))==0)dp[mask|(1<<v)][v]=(dp[mask|(1<<v)][v]+dp[mask][u])%MOD;return ""+dp[(1<<n)-1][n-1];',8,cases=D,directed=True,layout=DL.replace('n <= 80','n <= 16'))

W=[(n,[(u,v,(i*7+3)%19+1) for i,(u,v) in enumerate(e)]) for n,e in G]
WL=GL.replace('lines u v','lines u v weight')+' Edge weights are from 1 to 1000000.'
graph('weighted-source-shortest-paths','Weighted Source Shortest Paths','Print shortest-path distances from vertex 0 to every vertex in order, or -1 for an unreachable vertex.',
      lambda c:[x if x<10**12 else -1 for x in matrix(c)[0]],
      'long[]d=new long[n];Arrays.fill(d,Long.MAX_VALUE/4);d[0]=0;boolean[]used=new boolean[n];for(int k=0;k<n;k++){int u=-1;for(int i=0;i<n;i++)if(!used[i]&&(u<0||d[i]<d[u]))u=i;used[u]=true;for(int v=0;v<n;v++)if(a[u][v]!=0)d[v]=Math.min(d[v],d[u]+a[u][v]);}for(int i=0;i<n;i++)if(d[i]==Long.MAX_VALUE/4)d[i]=-1;return join(d);',7,cases=W,weighted=True,layout=WL)

def spanning(c,maximum=False):
    n,e=c
    if n==1:return 0
    totals=[]
    for chosen in combinations(e,n-1):
        if len(components((n,[(u,v) for u,v,w in chosen])))==1: totals.append(sum(w for u,v,w in chosen))
    return (max(totals) if maximum else min(totals)) if totals else -1
for maximum,slug,title in [(False,'minimum-spanning-tree-weight','Minimum Spanning Tree Weight'),(True,'maximum-spanning-tree-weight','Maximum Spanning Tree Weight')]:
    graph(slug,title,'Print the '+('largest' if maximum else 'smallest')+' total edge weight of a spanning tree, or -1 if disconnected. A single vertex has tree weight 0.',
          lambda c,maximum=maximum:spanning(c,maximum),
          'Arrays.sort(edges,(x,y)->Integer.compare('+('y[2],x[2]' if maximum else 'x[2],y[2]')+'));int[]p=new int[n];for(int i=0;i<n;i++)p[i]=i;long sum=0;int count=0;for(int[]e:edges){int u=e[0],v=e[1];while(u!=p[u])u=p[u];while(v!=p[v])v=p[v];if(u!=v){p[u]=v;sum+=e[2];count++;}}return ""+(count==n-1?sum:-1);',7,cases=W,weighted=True,layout=WL)

def bottleneck(c):
    n,e=c
    if n==1:return 0
    for w in sorted({w for u,v,w in e}):
        if any(0 in group and n-1 in group for group in components((n,[(u,v) for u,v,x in e if x<=w]))): return w
    return -1
graph('minimum-bottleneck-path','Minimum Bottleneck Path','Among paths from 0 to n-1, minimize the largest edge weight used. Print this minimum, -1 if unreachable, or 0 when n=1.',bottleneck,
      'long[]d=new long[n];Arrays.fill(d,Long.MAX_VALUE/4);d[0]=0;boolean[]used=new boolean[n];for(int k=0;k<n;k++){int u=-1;for(int i=0;i<n;i++)if(!used[i]&&(u<0||d[i]<d[u]))u=i;used[u]=true;for(int v=0;v<n;v++)if(a[u][v]!=0)d[v]=Math.min(d[v],Math.max(d[u],a[u][v]));}return ""+(d[n-1]==Long.MAX_VALUE/4?-1:d[n-1]);',7,cases=W,weighted=True,layout=WL)

def widest(c):
    n,e=c
    if n==1:return 0
    for w in sorted({w for u,v,w in e},reverse=True):
        if any(0 in group and n-1 in group for group in components((n,[(u,v) for u,v,x in e if x>=w]))): return w
    return -1
graph('widest-capacity-path','Widest Capacity Path','Edge weights are capacities. Maximize the smallest edge capacity along a path from 0 to n-1. Print the result, -1 if unreachable, or 0 when n=1.',widest,
      'int[]d=new int[n];Arrays.fill(d,-1);d[0]=Integer.MAX_VALUE;boolean[]used=new boolean[n];for(int k=0;k<n;k++){int u=-1;for(int i=0;i<n;i++)if(!used[i]&&(u<0||d[i]>d[u]))u=i;used[u]=true;for(int v=0;v<n;v++)if(a[u][v]!=0)d[v]=Math.max(d[v],Math.min(d[u],a[u][v]));}return ""+(n==1?0:d[n-1]);',7,cases=W,weighted=True,layout=WL)

TREES=[(4,[(0,1),(1,2),(1,3)]),(5,[(0,1),(1,2),(2,3),(3,4)]),(1,[]),(2,[(0,1)]),(5,[(0,1),(0,2),(0,3),(0,4)]),
       (7,[(0,1),(0,2),(1,3),(1,4),(2,5),(2,6)]),(6,[(0,1),(1,2),(2,3),(3,4),(4,5)]),
       (8,[(i//2,i) for i in range(1,8)]),(9,[(0,i) for i in range(1,9)]),(12,[(i-1,i) for i in range(1,12)])]
TL=GL+' The graph is guaranteed to be a tree (connected, with n-1 edges).'
graph('tree-diameter-length','Tree Diameter Length','Print the largest shortest-path edge distance between any two vertices.',lambda c:max(map(max,matrix(c))),
      'int start=0,best=0;for(int pass=0;pass<2;pass++){int[]d=new int[n];Arrays.fill(d,-1);d[start]=0;Deque<Integer>q=new ArrayDeque<>();q.add(start);while(!q.isEmpty()){int u=q.remove();if(d[u]>d[start])start=u;for(int v=0;v<n;v++)if(a[u][v]!=0&&d[v]<0){d[v]=d[u]+1;q.add(v);}}best=d[start];}return ""+best;',6,cases=TREES,layout=TL)
graph('minimum-height-tree-roots','Minimum Height Tree Roots','Rooting a tree at a vertex gives height equal to its furthest vertex distance. Print all roots with minimum height, in ascending order.',
      lambda c:(lambda ecc:[i for i,x in enumerate(ecc) if x==min(ecc)])([max(row) for row in matrix(c)]),
      'if(n==1)return "0";int[]deg=new int[n];Deque<Integer>q=new ArrayDeque<>();for(int i=0;i<n;i++){for(int j=0;j<n;j++)deg[i]+=a[i][j];if(deg[i]==1)q.add(i);}int remaining=n;while(remaining>2){int size=q.size();remaining-=size;while(size-->0){int u=q.remove();for(int v=0;v<n;v++)if(a[u][v]!=0&&--deg[v]==1)q.add(v);}}List<Integer>b=new ArrayList<>(q);Collections.sort(b);return join(b);',7,cases=TREES,layout=TL)
graph('tree-distance-sums','Tree Distance Sums','For every vertex in order, print the sum of edge distances from it to all vertices.',lambda c:list(map(sum,matrix(c))),
      'long[]ans=new long[n];for(int s=0;s<n;s++){int[]d=new int[n];Arrays.fill(d,-1);d[s]=0;Deque<Integer>q=new ArrayDeque<>();q.add(s);while(!q.isEmpty()){int u=q.remove();ans[s]+=d[u];for(int v=0;v<n;v++)if(a[u][v]!=0&&d[v]<0){d[v]=d[u]+1;q.add(v);}}}return join(ans);',6,cases=TREES,layout=TL)

def subtree(c):
    dist=matrix(c);return [sum(dist[0][j]==dist[0][i]+dist[i][j] for j in range(c[0])) for i in range(c[0])]
graph('rooted-subtree-sizes','Rooted Subtree Sizes','Root the tree at vertex 0. Print every vertex subtree size in vertex order, counting the vertex itself.',subtree,
      'int[]p=new int[n],order=new int[n],size=new int[n];Arrays.fill(p,-1);p[0]=0;order[0]=0;int count=1;for(int i=0;i<count;i++){int u=order[i];for(int v=0;v<n;v++)if(a[u][v]!=0&&p[v]<0){p[v]=u;order[count++]=v;}}Arrays.fill(size,1);for(int i=n-1;i>0;i--)size[p[order[i]]]+=size[order[i]];return join(size);',6,cases=TREES,layout=TL)

graph('tree-maximum-independent-set','Tree Maximum Independent Set','Print the largest number of vertices that can be selected with no edge connecting two selected vertices.',
      lambda c:max(sum(bits) for bits in product(range(2),repeat=c[0]) if all(not(bits[u] and bits[v]) for u,v in c[1])),
      'int[]p=new int[n],order=new int[n],take=new int[n],skip=new int[n];Arrays.fill(p,-1);p[0]=0;int count=1;for(int i=0;i<count;i++){int u=order[i];for(int v=0;v<n;v++)if(a[u][v]!=0&&p[v]<0){p[v]=u;order[count++]=v;}}Arrays.fill(take,1);for(int i=n-1;i>0;i--){int u=order[i];take[p[u]]+=skip[u];skip[p[u]]+=Math.max(take[u],skip[u]);}return ""+Math.max(take[0],skip[0]);',7,cases=TREES,layout=TL)

def core(c):
    remaining=set(range(c[0]))
    while True:
        remove={u for u in remaining if sum((x==u and y in remaining) or (y==u and x in remaining) for x,y in c[1])<2}
        if not remove:return len(remaining)
        remaining-=remove
graph('two-core-vertex-count','Two-Core Vertex Count','Repeatedly remove all vertices of degree less than 2 and their incident edges until no such vertices remain. Print how many vertices remain.',core,
      'int[]deg=new int[n];boolean[]gone=new boolean[n];Deque<Integer>q=new ArrayDeque<>();for(int i=0;i<n;i++){for(int j=0;j<n;j++)deg[i]+=a[i][j];if(deg[i]<2){q.add(i);gone[i]=true;}}int left=n;while(!q.isEmpty()){int u=q.remove();left--;for(int v=0;v<n;v++)if(a[u][v]!=0&&!gone[v]&&--deg[v]<2){gone[v]=true;q.add(v);}}return ""+left;',6)

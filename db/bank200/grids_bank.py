from collections import deque
from functools import lru_cache
from common import *

M=[[[1,2,3],[4,5,6]],[[1,3,1],[1,5,1],[4,2,1]],[[0]],[[7]],[[1,2,3,4]],[[4],[3],[2]],[[2,2],[2,2]],[[9,1,8],[2,7,3]],[[i*5+j for j in range(5)] for i in range(4)],[[((i*17+j*11)%23) for j in range(7)] for i in range(6)]]
B=[[[1,1,0],[0,1,1]],[[1,0,1],[0,1,0],[1,0,1]],[[0]],[[1]],[[0,0,0,0]],[[1],[0],[1]],[[1,1],[1,1]],[[0,0,0],[0,1,0],[0,0,0]],[[1 if i==j or i+j==4 else 0 for j in range(5)] for i in range(5)],[[int((i*17+j*11)%7<4) for j in range(8)] for i in range(7)]]
L='rows cols, then rows lines of cols integers. 1 <= rows, cols <= 30; 0 <= cell <= 1000.'
BL=L.replace('0 <= cell <= 1000','each cell is 0 or 1')
R='int h=in.nextInt(),w=in.nextInt();int[][]a=new int[h][w];for(int i=0;i<h;i++)for(int j=0;j<w;j++)a[i][j]=in.nextInt();'
DIR='int[]dr={-1,0,1,0},dc={0,1,0,-1};'

def encode(a):return f'{len(a)} {len(a[0])}\n'+''.join(' '.join(map(str,r))+'\n' for r in a)
def rows(a):return '\n'.join(' '.join(map(str,r)) for r in a)
def near(a,r,c):return [(i,j) for i,j in [(r-1,c),(r+1,c),(r,c-1),(r,c+1)] if 0<=i<len(a) and 0<=j<len(a[0])]

def grid(slug,title,task,oracle,java,d=6,cases=M,layout=L):
    add(slug,title,d,task,layout,cases,encode,oracle,R+java)

grid('matrix-transpose','Matrix Transpose','Print the transposed matrix as cols rows, without a dimensions header.',
     lambda a:rows(list(zip(*a))),
     r'StringBuilder b=new StringBuilder();for(int j=0;j<w;j++){if(j>0)b.append("\n");for(int i=0;i<h;i++){if(i>0)b.append(" ");b.append(a[i][j]);}}return b.toString();',4)

def spiral(a):
    a=[r[:] for r in a];b=[]
    while a:
        b+=a.pop(0)
        a=[list(r) for r in zip(*a)][::-1]
    return b
grid('spiral-matrix-order','Spiral Matrix Order','Print all values in clockwise spiral order, starting at the top-left corner and initially moving right.',spiral,
     'List<Integer>b=new ArrayList<>();int top=0,bottom=h-1,left=0,right=w-1;while(top<=bottom&&left<=right){for(int j=left;j<=right;j++)b.add(a[top][j]);top++;for(int i=top;i<=bottom;i++)b.add(a[i][right]);right--;if(top<=bottom){for(int j=right;j>=left;j--)b.add(a[bottom][j]);bottom--;}if(left<=right){for(int i=bottom;i>=top;i--)b.add(a[i][left]);left++;}}return join(b);',5)
grid('rotate-matrix-clockwise','Rotate Matrix Clockwise','Rotate the rectangular matrix clockwise by 90 degrees. Print cols rows of values without a dimensions header.',
     lambda a:rows(list(zip(*a[::-1]))),
     r'StringBuilder b=new StringBuilder();for(int j=0;j<w;j++){if(j>0)b.append("\n");for(int i=h-1;i>=0;i--){if(i<h-1)b.append(" ");b.append(a[i][j]);}}return b.toString();',4)

def pathsum(a):
    @lru_cache(None)
    def f(i,j):
        if i==len(a)-1 and j==len(a[0])-1:return a[i][j]
        return a[i][j]+min(f(x,y) for x,y in [(i+1,j),(i,j+1)] if x<len(a) and y<len(a[0]))
    return f(0,0)
grid('minimum-grid-path-sum','Minimum Grid Path Sum','Move only right or down from the top-left to the bottom-right. Print the minimum sum of all visited cells, including both endpoints.',pathsum,
     'long[][]d=new long[h][w];for(int i=0;i<h;i++)for(int j=0;j<w;j++){if(i==0&&j==0)d[i][j]=a[i][j];else d[i][j]=a[i][j]+Math.min(i>0?d[i-1][j]:Long.MAX_VALUE/4,j>0?d[i][j-1]:Long.MAX_VALUE/4);}return ""+d[h-1][w-1];',5)

def paths(a):
    @lru_cache(None)
    def f(i,j):
        if i>=len(a) or j>=len(a[0]) or a[i][j]:return 0
        if i==len(a)-1 and j==len(a[0])-1:return 1
        return (f(i+1,j)+f(i,j+1))%1000000007
    return f(0,0)
grid('obstacle-grid-path-count','Obstacle Grid Path Count','Zero cells are open and one cells are blocked. Count paths from top-left to bottom-right using only right/down moves, modulo 1000000007. A blocked endpoint gives zero paths.',paths,
     'long[]d=new long[w];d[0]=1;for(int i=0;i<h;i++)for(int j=0;j<w;j++){if(a[i][j]==1)d[j]=0;else if(j>0)d[j]=(d[j]+d[j-1])%MOD;}return ""+d[w-1];',6,cases=B,layout=BL)

def squares(a):
    return [k for i in range(len(a)) for j in range(len(a[0])) for k in range(1,min(len(a)-i,len(a[0])-j)+1) if all(a[x][y] for x in range(i,i+k) for y in range(j,j+k))]
SQ='int[][]d=new int[h+1][w+1];long count=0;int best=0;for(int i=1;i<=h;i++)for(int j=1;j<=w;j++)if(a[i-1][j-1]==1){d[i][j]=1+Math.min(d[i-1][j-1],Math.min(d[i-1][j],d[i][j-1]));count+=d[i][j];best=Math.max(best,d[i][j]);}'
grid('all-one-square-count','All-One Square Count','Count square submatrices containing only ones. Each position and size counts separately.',lambda a:len(squares(a)),SQ+'return ""+count;',6,cases=B,layout=BL)
grid('largest-all-one-square-area','Largest All-One Square Area','Print the area, not side length, of the largest square containing only ones, or 0.',lambda a:max(squares(a)+[0])**2,SQ+'return ""+(best*best);',6,cases=B,layout=BL)

def rectangle(a):
    return max([0]+[(x-i)*(y-j) for i in range(len(a)) for j in range(len(a[0])) for x in range(i+1,len(a)+1) for y in range(j+1,len(a[0])+1) if all(a[r][c] for r in range(i,x) for c in range(j,y))])
grid('largest-all-one-rectangle','Largest All-One Rectangle','Print the maximum area of a rectangular submatrix containing only ones, or 0.',rectangle,
     'int[]height=new int[w+1];int best=0;for(int i=0;i<h;i++){for(int j=0;j<w;j++)height[j]=a[i][j]==0?0:height[j]+1;Deque<Integer>s=new ArrayDeque<>();for(int j=0;j<=w;j++){while(!s.isEmpty()&&height[s.peek()]>height[j]){int k=s.pop();int width=s.isEmpty()?j:j-s.peek()-1;best=Math.max(best,height[k]*width);}s.push(j);}}return ""+best;',7,cases=B,layout=BL)

def islands(a):
    cells={(i,j) for i,row in enumerate(a) for j,x in enumerate(row) if x==1};out=[]
    while cells:
        group={cells.pop()};q=list(group)
        for i,j in q:
            for p in near(a,i,j):
                if p in cells:cells.remove(p);group.add(p);q.append(p)
        out.append(group)
    return out
ISLAND=DIR+'boolean[][]seen=new boolean[h][w];List<List<int[]>>groups=new ArrayList<>();for(int i=0;i<h;i++)for(int j=0;j<w;j++)if(a[i][j]==1&&!seen[i][j]){List<int[]>g=new ArrayList<>();Deque<int[]>q=new ArrayDeque<>();q.add(new int[]{i,j});seen[i][j]=true;while(!q.isEmpty()){int[]u=q.remove();g.add(u);for(int k=0;k<4;k++){int r=u[0]+dr[k],c=u[1]+dc[k];if(r>=0&&r<h&&c>=0&&c<w&&a[r][c]==1&&!seen[r][c]){seen[r][c]=true;q.add(new int[]{r,c});}}}groups.add(g);}'
grid('largest-island-area','Largest Island Area','An island is a four-directionally connected group of ones. Print its maximum number of cells, or 0.',lambda a:max([len(g) for g in islands(a)]+[0]),
     ISLAND+'int best=0;for(List<int[]>g:groups)best=Math.max(best,g.size());return ""+best;',6,cases=B,layout=BL)
grid('total-land-perimeter','Total Land Perimeter','Print the total number of land-cell sides touching water or the grid boundary. Ones are land; zeros are water. Count all islands and internal lake boundaries.',
     lambda a:sum(4-sum(a[x][y] for x,y in near(a,i,j)) for i,row in enumerate(a) for j,v in enumerate(row) if v),
     DIR+'long ans=0;for(int i=0;i<h;i++)for(int j=0;j<w;j++)if(a[i][j]==1)for(int k=0;k<4;k++){int r=i+dr[k],c=j+dc[k];if(r<0||r>=h||c<0||c>=w||a[r][c]==0)ans++;}return ""+ans;',5,cases=B,layout=BL)
grid('enclosed-land-cell-count','Enclosed Land Cell Count','Count land cells (ones) that cannot reach any boundary land cell using four-directional land steps.',
     lambda a:sum(len(g) for g in islands(a) if all(i not in (0,len(a)-1) and j not in (0,len(a[0])-1) for i,j in g)),
     ISLAND+'int ans=0;for(List<int[]>g:groups){boolean edge=false;for(int[]p:g)if(p[0]==0||p[0]==h-1||p[1]==0||p[1]==w-1)edge=true;if(!edge)ans+=g.size();}return ""+ans;',6,cases=B,layout=BL)

def shortest(a):
    if a[0][0] or a[-1][-1]:return -1
    q=deque([(0,0,0)]);seen={(0,0)}
    while q:
        i,j,d=q.popleft()
        if (i,j)==(len(a)-1,len(a[0])-1):return d
        for x,y in near(a,i,j):
            if not a[x][y] and (x,y) not in seen:seen.add((x,y));q.append((x,y,d+1))
    return -1
grid('shortest-four-way-grid-path','Shortest Four-Way Grid Path','Zero cells are open and ones blocked. Print the minimum number of four-directional moves from top-left to bottom-right, or -1. A single open cell needs 0 moves.',shortest,
     DIR+'if(a[0][0]!=0||a[h-1][w-1]!=0)return "-1";int[][]d=new int[h][w];for(int[]row:d)Arrays.fill(row,-1);d[0][0]=0;Deque<int[]>q=new ArrayDeque<>();q.add(new int[]{0,0});while(!q.isEmpty()){int[]u=q.remove();for(int k=0;k<4;k++){int r=u[0]+dr[k],c=u[1]+dc[k];if(r>=0&&r<h&&c>=0&&c<w&&a[r][c]==0&&d[r][c]<0){d[r][c]=d[u[0]][u[1]]+1;q.add(new int[]{r,c});}}}return ""+d[h-1][w-1];',6,cases=B,layout=BL)

def nearest(a):
    zeros=[(i,j) for i,row in enumerate(a) for j,x in enumerate(row) if x==0]
    return rows([[min([abs(i-x)+abs(j-y) for x,y in zeros] or [-1]) for j in range(len(a[0]))] for i in range(len(a))])
PRINT=r'StringBuilder b=new StringBuilder();for(int i=0;i<h;i++){if(i>0)b.append("\n");b.append(join(d[i]));}return b.toString();'
grid('nearest-zero-distances','Nearest Zero Distances','Print a matrix of the minimum four-directional distances to any zero. All cells can be traversed. If there is no zero, print -1 for every cell.',nearest,
     DIR+'int[][]d=new int[h][w];Deque<int[]>q=new ArrayDeque<>();for(int i=0;i<h;i++)for(int j=0;j<w;j++){d[i][j]=a[i][j]==0?0:-1;if(d[i][j]==0)q.add(new int[]{i,j});}while(!q.isEmpty()){int[]u=q.remove();for(int k=0;k<4;k++){int r=u[0]+dr[k],c=u[1]+dc[k];if(r>=0&&r<h&&c>=0&&c<w&&d[r][c]<0){d[r][c]=d[u[0]][u[1]]+1;q.add(new int[]{r,c});}}}'+PRINT,6,cases=B,layout=BL)

OR=[[[2,1,1],[1,1,0],[0,1,1]],[[2,1,1],[0,1,1],[1,0,1]],[[0]],[[1]],[[2]],[[2,1,1,1]],[[1],[0],[2]],[[2,1],[1,2]],[[1,1],[1,1]],[[2,1,0,1],[1,1,1,1],[0,1,2,0]]]
def rot(a):
    a=[r[:] for r in a];minutes=0
    while True:
        fresh={(i,j) for i,row in enumerate(a) for j,x in enumerate(row) if x==1}
        if not fresh:return minutes
        change={(i,j) for i,j in fresh if any(a[x][y]==2 for x,y in near(a,i,j))}
        if not change:return -1
        for i,j in change:a[i][j]=2
        minutes+=1
grid('rotting-oranges-minutes','Rotting Oranges Minutes','0 is empty, 1 fresh, 2 rotten. Each minute rotten oranges simultaneously rot their four-directional fresh neighbors. Print minutes until no fresh oranges remain, or -1 if impossible.',rot,
     DIR+'Deque<int[]>q=new ArrayDeque<>();int fresh=0;for(int i=0;i<h;i++)for(int j=0;j<w;j++){if(a[i][j]==1)fresh++;if(a[i][j]==2)q.add(new int[]{i,j,0});}int minutes=0;while(!q.isEmpty()){int[]u=q.remove();minutes=Math.max(minutes,u[2]);for(int k=0;k<4;k++){int r=u[0]+dr[k],c=u[1]+dc[k];if(r>=0&&r<h&&c>=0&&c<w&&a[r][c]==1){a[r][c]=2;fresh--;q.add(new int[]{r,c,u[2]+1});}}}return ""+(fresh>0?-1:minutes);',6,cases=OR,layout=L.replace('0 <= cell <= 1000','each cell is 0, 1, or 2'))

def increasing(a):
    @lru_cache(None)
    def f(i,j):return 1+max([f(x,y) for x,y in near(a,i,j) if a[x][y]>a[i][j]]+[0])
    return max(f(i,j) for i in range(len(a)) for j in range(len(a[0])))
grid('longest-increasing-grid-path','Longest Increasing Grid Path','Move in four directions only to a strictly larger value. Print the maximum number of cells in a path starting anywhere.',increasing,
     DIR+'List<int[]>cells=new ArrayList<>();for(int i=0;i<h;i++)for(int j=0;j<w;j++)cells.add(new int[]{i,j});cells.sort((x,y)->Integer.compare(a[x[0]][x[1]],a[y[0]][y[1]]));int[][]d=new int[h][w];int best=1;for(int[]u:cells){int i=u[0],j=u[1];d[i][j]=1;for(int k=0;k<4;k++){int r=i+dr[k],c=j+dc[k];if(r>=0&&r<h&&c>=0&&c<w&&a[r][c]<a[i][j])d[i][j]=Math.max(d[i][j],d[r][c]+1);}best=Math.max(best,d[i][j]);}return ""+best;',7)

NEG=[[[x-5 for x in row] for row in a] for a in M]
def dungeon(a):
    def f(i,j,total,minimum):
        total+=a[i][j];minimum=min(minimum,total)
        if i==len(a)-1 and j==len(a[0])-1:return 1-minimum
        return min(f(x,y,total,minimum) for x,y in [(i+1,j),(i,j+1)] if x<len(a) and y<len(a[0]))
    return f(0,0,0,0)
grid('minimum-dungeon-health','Minimum Dungeon Health','Enter at top-left and move only right/down to bottom-right. Each cell changes health by its value, including endpoints. Health must always be at least 1. Print minimum starting health.',dungeon,
     'long[][]d=new long[h+1][w+1];for(long[]row:d)Arrays.fill(row,Long.MAX_VALUE/4);d[h][w-1]=d[h-1][w]=1;for(int i=h-1;i>=0;i--)for(int j=w-1;j>=0;j--)d[i][j]=Math.max(1,Math.min(d[i+1][j],d[i][j+1])-a[i][j]);return ""+d[0][0];',7,cases=NEG,layout=L.replace('0 <= cell <= 1000','-1000 <= cell <= 1000'))

def falling(a):
    @lru_cache(None)
    def f(i,j):return a[i][j]+(min(f(i+1,k) for k in range(max(0,j-1),min(len(a[0]),j+2))) if i+1<len(a) else 0)
    return min(f(0,j) for j in range(len(a[0])))
grid('minimum-falling-path-sum','Minimum Falling Path Sum','Start anywhere in the top row. Move to the next row in the same or an adjacent column. Print minimum sum reaching the bottom row, including endpoints.',falling,
     'long[]d=new long[w];for(int i=0;i<h;i++){long[]e=new long[w];for(int j=0;j<w;j++){long best=d[j];if(j>0)best=Math.min(best,d[j-1]);if(j+1<w)best=Math.min(best,d[j+1]);e[j]=a[i][j]+best;}d=e;}long best=d[0];for(long x:d)best=Math.min(best,x);return ""+best;',6,cases=NEG,layout=L.replace('0 <= cell <= 1000','-1000 <= cell <= 1000'))

grid('zero-matrix-rows-and-columns','Zero Matrix Rows and Columns','For every zero in the original matrix, replace its entire row and column with zeros. Print the resulting matrix without dimensions. Newly created zeros do not trigger more changes.',
     lambda a:rows([[0 if 0 in a[i] or any(row[j]==0 for row in a) else x for j,x in enumerate(a[i])] for i in range(len(a))]),
     'boolean[]r=new boolean[h],c=new boolean[w];for(int i=0;i<h;i++)for(int j=0;j<w;j++)if(a[i][j]==0){r[i]=true;c[j]=true;}int[][]d=new int[h][w];for(int i=0;i<h;i++)for(int j=0;j<w;j++)d[i][j]=r[i]||c[j]?0:a[i][j];'+PRINT,5)

GOLD=[[[0,6,0],[5,8,7],[0,9,0]],[[1,0,7],[2,0,6],[3,4,5]],[[0]],[[7]],[[1,2,3,4]],[[4],[0],[2]],[[2,2],[2,2]],[[9,1,8],[2,7,3]],[[0,1,0],[1,0,1]],[[1,2,3],[4,0,5],[6,7,8]]]
def gold(a):
    def f(i,j,seen):return a[i][j]+max([f(x,y,seen|{(x,y)}) for x,y in near(a,i,j) if a[x][y]>0 and (x,y) not in seen]+[0])
    return max([f(i,j,{(i,j)}) for i,row in enumerate(a) for j,x in enumerate(row) if x>0]+[0])
grid('maximum-gold-path','Maximum Gold Path','Start at any positive cell and move in four directions through positive cells without revisiting a cell. You may stop anywhere. Print the maximum collected sum, or 0.',gold,
     DIR+'int count=h*w;long best=0;Deque<long[]>stack=new ArrayDeque<>();for(int i=0;i<h;i++)for(int j=0;j<w;j++)if(a[i][j]>0)stack.push(new long[]{i,j,1L<<(i*w+j),a[i][j]});while(!stack.isEmpty()){long[]u=stack.pop();best=Math.max(best,u[3]);for(int k=0;k<4;k++){int r=(int)u[0]+dr[k],c=(int)u[1]+dc[k];if(r>=0&&r<h&&c>=0&&c<w&&a[r][c]>0){long bit=1L<<(r*w+c);if((u[2]&bit)==0)stack.push(new long[]{r,c,u[2]|bit,u[3]+a[r][c]});}}}return ""+best;',8,cases=GOLD,layout=L+' Additionally rows * cols <= 16.')

def shapes(a):
    return len({tuple(sorted((i-min(x for x,y in g),j-min(y for x,y in g)) for i,j in g)) for g in islands(a)})
grid('distinct-island-shapes','Distinct Island Shapes','Count distinct four-connected island shapes. Ones are land. Translation preserves shape, but rotation and reflection do not.',shapes,
     ISLAND+'Set<String>shapes=new HashSet<>();for(List<int[]>g:groups){int r=h,c=w;for(int[]p:g){r=Math.min(r,p[0]);c=Math.min(c,p[1]);}List<String>cells=new ArrayList<>();for(int[]p:g)cells.add((p[0]-r)+","+(p[1]-c));Collections.sort(cells);shapes.add(String.join(";",cells));}return ""+shapes.size();',7,cases=B,layout=BL)

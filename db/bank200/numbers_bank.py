import math
from collections import Counter
from itertools import permutations
from common import *

def number(slug,title,task,layout,cases,oracle,body,d=6):
    add(slug,title,d,task,layout,cases,numbers_input,oracle,body)

def prime(n):return n>=2 and all(n%d for d in range(2,math.isqrt(n)+1))
NS=[10,20,0,1,2,3,100,997,10000,100000]
number('count-primes-up-to-n','Count Primes up to N','Print the number of prime integers less than or equal to n.',
       'One integer n, 0 <= n <= 1000000.',NS,lambda n:sum(prime(x) for x in range(2,n+1)),
       'int n=in.nextInt();boolean[]composite=new boolean[n+1];int count=0;for(int i=2;i<=n;i++){if(!composite[i]){count++;if((long)i*i<=n)for(int j=i*i;j<=n;j+=i)composite[j]=true;}}return ""+count;',5)

def nth(n):
    x=1
    while n:
        x+=1
        if prime(x):n-=1
    return x
number('nth-prime-number','Nth Prime Number','Print the nth prime, with 2 being the first prime.',
       'One integer n, 1 <= n <= 10000.',[6,10,1,2,3,25,100,500,1000,2000],nth,
       'int n=in.nextInt(),limit=200000;boolean[]b=new boolean[limit+1];int count=0;for(int i=2;i<=limit;i++)if(!b[i]){if(++count==n)return ""+i;if((long)i*i<=limit)for(int j=i*i;j<=limit;j+=i)b[j]=true;}throw new IllegalStateException();',5)

PN=[60,97,1,2,16,81,1001,99991,1000000000,999983]
def factors(n):
    out=[];d=2
    while d*d<=n:
        while n%d==0:out.append(d);n//=d
        d+=1
    if n>1:out.append(n)
    return out
number('prime-factor-multiset','Prime Factor Multiset','Print all prime factors in ascending order, repeated according to multiplicity. Print EMPTY for 1.',
       'One integer n, 1 <= n <= 1000000000.',PN,factors,
       'long n=in.nextLong();List<Long>b=new ArrayList<>();for(long d=2;d*d<=n;d++)while(n%d==0){b.add(d);n/=d;}if(n>1)b.add(n);return join(b);',5)

TN=[9,10,1,2,12,36,97,100,1000,9999]
number('euler-totient','Euler Totient','Print the count of integers from 1 through n that are coprime to n. For n=1 the answer is 1.',
       'One integer n, 1 <= n <= 1000000000.',TN,lambda n:sum(math.gcd(i,n)==1 for i in range(1,n+1)),
       'long n=in.nextLong(),answer=n;for(long p=2;p*p<=n;p++)if(n%p==0){while(n%p==0)n/=p;answer-=answer/p;}if(n>1)answer-=answer/n;return ""+answer;',6)

number('summatory-totient','Summatory Totient','Print the sum of phi(i) for 1 <= i <= n, where phi(i) counts integers in 1..i coprime to i and phi(1)=1.',
       'One integer n, 1 <= n <= 1000000.',[5,10,1,2,3,12,30,50,100,300],
       lambda n:sum(math.gcd(i,j)==1 for i in range(1,n+1) for j in range(1,i+1)),
       'int n=in.nextInt();long[]phi=new long[n+1];for(int i=0;i<=n;i++)phi[i]=i;for(int p=2;p<=n;p++)if(phi[p]==p)for(int j=p;j<=n;j+=p)phi[j]-=phi[j]/p;long ans=0;for(int i=1;i<=n;i++)ans+=phi[i];return ""+ans;',7)

number('positive-divisor-count','Positive Divisor Count','Print the number of positive divisors of n.',
       'One integer n, 1 <= n <= 1000000000.',PN,
       lambda n:sum(1 if d*d==n else 2 for d in range(1,math.isqrt(n)+1) if n%d==0),
       'long n=in.nextLong(),ans=1;for(long p=2;p*p<=n;p++)if(n%p==0){int e=0;while(n%p==0){n/=p;e++;}ans*=e+1;}if(n>1)ans*=2;return ""+ans;',5)
number('positive-divisor-sum','Positive Divisor Sum','Print the sum of all positive divisors of n.',
       'One integer n, 1 <= n <= 1000000000.',PN,
       lambda n:sum(d if d*d==n else d+n//d for d in range(1,math.isqrt(n)+1) if n%d==0),
       'long n=in.nextLong(),ans=1;for(long p=2;p*p<=n;p++)if(n%p==0){long sum=1,power=1;while(n%p==0){n/=p;power*=p;sum+=power;}ans*=sum;}if(n>1)ans*=1+n;return ""+ans;',5)

def trailing(n,b=10):
    f=math.factorial(n);ans=0
    while f%b==0:ans+=1;f//=b
    return ans
number('factorial-trailing-zeros','Factorial Trailing Zeros','Print the number of trailing decimal zeros in n factorial. 0 factorial is 1.',
       'One integer n, 0 <= n <= 1000000000000000000.',[5,25,0,1,4,10,100,125,1000,5000],trailing,
       'long n=in.nextLong(),ans=0;while(n>0){n/=5;ans+=n;}return ""+ans;',5)

BC=[(5,2),(10,3),(0,0),(1,0),(1,1),(20,10),(100,50),(500,4),(1000,500),(2000,1000)]
number('binomial-coefficient-modulo','Binomial Coefficient Modulo','Print n choose k modulo 1000000007.',
       'n k; 0 <= k <= n <= 100000.',BC,lambda c:math.comb(*c)%1000000007,
       'int n=in.nextInt(),k=in.nextInt();k=Math.min(k,n-k);long a=1,b=1;for(int i=1;i<=k;i++){a=a*(n-k+i)%MOD;b=b*i%MOD;}return ""+(a*power(b,MOD-2,MOD)%MOD);',6)
number('balanced-parenthesis-sequence-count','Balanced Parenthesis Sequence Count','Count correctly balanced sequences of n pairs of parentheses, modulo 1000000007. For zero pairs count the empty sequence.',
       'One integer n, 0 <= n <= 1000.',[3,4,0,1,2,5,10,25,100,300],lambda n:(math.comb(2*n,n)//(n+1))%1000000007,
       'int n=in.nextInt();long[]d=new long[n+1];d[0]=1;for(int i=1;i<=n;i++)for(int j=0;j<i;j++)d[i]=(d[i]+d[j]*d[i-1-j])%MOD;return ""+d[n];',6)

def derange(n):return sum((-1)**i*(math.factorial(n)//math.factorial(i)) for i in range(n+1))%1000000007
number('derangement-count','Derangement Count','Count permutations of n distinct items in which no item stays in its original position, modulo 1000000007. The empty permutation counts once.',
       'One integer n, 0 <= n <= 1000000.',[3,4,0,1,2,5,10,20,50,100],derange,
       'int n=in.nextInt();if(n==0)return "1";long a=1,b=0;for(int i=2;i<=n;i++){long c=(i-1)*(a+b)%MOD;a=b;b=c;}return ""+b;',6)

POW=[(2,10,1000),(3,5,7),(0,0,7),(0,5,13),(123,0,1),(7,1,7),(1000000000,1000000000000000000,1000000007),(987654321,123456789,999983),(2,63,1000000007),(999999999,999999,97)]
number('modular-exponentiation','Modular Exponentiation','Print a raised to b modulo m. Treat a raised to zero as 1, including a=0.',
       'a b m; 0 <= a <= 1000000000; 0 <= b <= 1000000000000000000; 1 <= m <= 1000000007.',POW,lambda c:pow(*c),
       'long a=in.nextLong(),b=in.nextLong(),m=in.nextLong();a%=m;long ans=1%m;while(b>0){if((b&1)==1)ans=ans*a%m;a=a*a%m;b>>=1;}return ""+ans;',6)

INV=[(3,11),(2,4),(1,2),(0,7),(10,17),(7,19),(25,100),(17,3120),(12345,65537),(99991,1000000007)]
number('modular-multiplicative-inverse','Modular Multiplicative Inverse','Print the smallest nonnegative x with a*x congruent to 1 modulo m, or -1 if no inverse exists.',
       'a m; 0 <= a <= 1000000000; 2 <= m <= 1000000007.',INV,lambda c:pow(c[0],-1,c[1]) if math.gcd(*c)==1 else -1,
       'long a=in.nextLong(),m=in.nextLong(),r0=a,r1=m,s0=1,s1=0;while(r1!=0){long q=r0/r1,t=r0-q*r1;r0=r1;r1=t;t=s0-q*s1;s0=s1;s1=t;}return ""+(r0==1?(s0%m+m)%m:-1);',7)

CRT=[(2,3,3,5),(1,2,2,3),(0,2,0,3),(1,3,0,2),(4,5,6,7),(0,7,1,11),(7,8,8,9),(3,11,5,13),(16,17,18,19),(45,97,81,101)]
number('two-congruence-reconstruction','Two-Congruence Reconstruction','Print the smallest nonnegative x satisfying x mod m = a and x mod n = b. The moduli are guaranteed coprime.',
       'a m b n; 2 <= m,n <= 1000000; 0 <= a < m; 0 <= b < n; gcd(m,n)=1.',CRT,
       lambda c:next(x for x in range(c[0],c[1]*c[3],c[1]) if x%c[3]==c[2]),
       'long a=in.nextLong(),m=in.nextLong(),b=in.nextLong(),n=in.nextLong();long r0=m,r1=n,s0=1,s1=0;while(r1!=0){long q=r0/r1,t=r0-q*r1;r0=r1;r1=t;t=s0-q*s1;s0=s1;s1=t;}long k=(((b-a)%n+n)%n*((s0%n+n)%n))%n;return ""+(a+m*k);',7)

AND=[(5,7),(0,1),(0,0),(1,1),(8,15),(16,31),(12,13),(100,110),(1024,2047),(2147483600,2147483647)]
number('inclusive-range-bitwise-and','Inclusive Range Bitwise AND','Print the bitwise AND of all integers in the inclusive range left through right.',
       'left right; 0 <= left <= right <= 2147483647.',AND,
       lambda c:__import__('functools').reduce(int.__and__,range(c[0],c[1]+1)),
       'long l=in.nextLong(),r=in.nextLong();int shift=0;while(l!=r){l>>=1;r>>=1;shift++;}return ""+(l<<shift);',6)

number('total-set-bits-through-n','Total Set Bits through N','Print the total number of set bits in binary representations of all integers from 0 through n.',
       'One integer n, 0 <= n <= 1000000000000.',[5,7,0,1,2,3,16,100,1023,100000],lambda n:sum(i.bit_count() for i in range(n+1)),
       'long n=in.nextLong(),ans=0;for(long bit=1;bit<=n;bit<<=1){long cycle=bit<<1,count=n+1;ans+=(count/cycle)*bit+Math.max(0,count%cycle-bit);}return ""+ans;',7)

UN=[[2,2,3,2],[0,1,0,1,0,1,99],[7],[-1],[-2,-2,-2,5],[9,4,9,4,9,4,-7],[0,0,0,-2147483648],[2147483647,1,1,1],[3,3,3,4,4,4,5,5,5,0],[-8,-8,6,-8]]
add('single-value-among-triples','Single Value among Triples',6,'Every value occurs exactly three times except one that occurs once. Print that value using constant extra space.',
    'n followed by n signed 32-bit integers; 1 <= n <= 100000. The frequency guarantee always holds.',UN,arr_input,
    lambda a:next(x for x,c in Counter(a).items() if c==1),
    'int n=in.nextInt(),ones=0,twos=0;for(int i=0;i<n;i++){int x=in.nextInt();ones=(ones^x)&~twos;twos=(twos^x)&~ones;}return ""+ones;')

ADD=[(3,5),(-3,5),(0,0),(1,-1),(-5,-7),(1000000000,1000000000),(-1000000000,-1000000000),(2147483647,0),(-2147483648,0),(1023,1)]
number('sum-using-bitwise-operations','Sum Using Bitwise Operations','Compute a+b without using arithmetic + or - operators in your algorithm. The result is guaranteed to fit a signed 32-bit integer.',
       'Two signed 32-bit integers a and b.',ADD,sum,
       'int a=in.nextInt(),b=in.nextInt();while(b!=0){int carry=(a&b)<<1;a^=b;b=carry;}return Integer.toString(a);',6)

number('integer-square-root','Integer Square Root','Print floor(sqrt(n)) without floating-point arithmetic.',
       'One integer n, 0 <= n <= 9223372036854775807.',[8,16,0,1,2,3,999999999,1000000000000,9223372036854775807,999999999999999999],math.isqrt,
       'long n=in.nextLong(),l=0,r=Math.min(n,3037000499L),ans=0;while(l<=r){long m=l+(r-l)/2;if(m==0||m<=n/m){ans=m;l=m+1;}else r=m-1;}return ""+ans;',6)

def happy(n):
    seen=set()
    while n!=1 and n not in seen:
        seen.add(n);n=sum(int(c)**2 for c in str(n))
    return n==1
number('happy-number-check','Happy Number Check','Repeatedly replace n by the sum of squares of its decimal digits. Print true if this eventually reaches 1, otherwise false.',
       'One integer n, 1 <= n <= 2147483647.',[19,2,1,7,10,13,20,999,1000000000,2147483647],happy,
       'int n=in.nextInt();Set<Integer>seen=new HashSet<>();while(n!=1&&seen.add(n)){int s=0;while(n>0){int d=n%10;s+=d*d;n/=10;}n=s;}return ""+(n==1);',5)

PERM=[(3,3),(4,9),(1,1),(2,1),(2,2),(3,6),(4,24),(5,42),(6,500),(7,4000)]
number('kth-lexicographic-permutation','Kth Lexicographic Permutation','Print the kth lexicographic permutation of 1 through n as space-separated numbers. k is one-based.',
       'n k; 1 <= n <= 12; 1 <= k <= n!.',PERM,
       lambda c:next(p for i,p in enumerate(permutations(range(1,c[0]+1)),1) if i==c[1]),
       'int n=in.nextInt();long k=in.nextLong()-1;long[]f=new long[n+1];f[0]=1;List<Integer>pool=new ArrayList<>(),ans=new ArrayList<>();for(int i=1;i<=n;i++){f[i]=f[i-1]*i;pool.add(i);}for(int i=n;i>0;i--){int j=(int)(k/f[i-1]);k%=f[i-1];ans.add(pool.remove(j));}return join(ans);',7)

def josephus(c):
    n,k=c;a=list(range(n));i=0
    while len(a)>1:i=(i+k-1)%len(a);a.pop(i)
    return a[0]
number('josephus-survivor','Josephus Survivor','People numbered 0 through n-1 stand in a circle. Starting at 0, count k people and remove the kth, then resume with the next person. Print the last remaining number.',
       'n k; 1 <= n <= 1000000; 1 <= k <= 1000000000.',[(5,2),(7,3),(1,1),(1,99),(2,1),(2,2),(10,1),(20,7),(100,17),(1000,999)],josephus,
       'int n=in.nextInt();long k=in.nextLong(),ans=0;for(int size=2;size<=n;size++)ans=(ans+k)%size;return ""+ans;',6)

FB=[(10,12),(25,10),(0,2),(1,10),(5,2),(10,16),(20,27),(100,100),(250,7),(1000,72)]
number('factorial-zeros-in-base','Factorial Zeros in a Base','Print how many trailing zero digits n factorial has when written in base b.',
       'n b; 0 <= n <= 1000000000000; 2 <= b <= 1000000000.',FB,lambda c:trailing(*c),
       'long n=in.nextLong(),b=in.nextLong(),ans=Long.MAX_VALUE;for(long p=2;p*p<=b;p++)if(b%p==0){int e=0;while(b%p==0){b/=p;e++;}long x=n,count=0;while(x>0){x/=p;count+=x;}ans=Math.min(ans,count/e);}if(b>1){long x=n,count=0;while(x>0){x/=b;count+=x;}ans=Math.min(ans,count);}return ""+ans;',7)

number('count-decimal-ones','Count Decimal Ones','Count occurrences of digit 1 in ordinary decimal representations of all integers from 0 through n.',
       'One integer n, 0 <= n <= 1000000000000.',[13,20,0,1,9,10,99,101,1111,100000],lambda n:sum(str(i).count('1') for i in range(n+1)),
       'long n=in.nextLong(),ans=0;for(long f=1;f<=n;f*=10){long high=n/f/10,cur=n/f%10,low=n%f;ans+=high*f;if(cur==1)ans+=low+1;else if(cur>1)ans+=f;}return ""+ans;',7)

def fib(n):
    def mul(a,b):return [[sum(a[i][k]*b[k][j] for k in range(2))%1000000007 for j in range(2)] for i in range(2)]
    r=[[1,0],[0,1]];a=[[1,1],[1,0]]
    while n:
        if n&1:r=mul(r,a)
        a=mul(a,a);n//=2
    return r[0][1]
number('large-index-fibonacci','Large-Index Fibonacci','Print F(n) modulo 1000000007, where F(0)=0, F(1)=1, and F(n)=F(n-1)+F(n-2).',
       'One integer n, 0 <= n <= 1000000000000000000.',[10,50,0,1,2,3,100,1000,1000000,1000000000000000000],fib,
       'long n=in.nextLong(),a=0,b=1;for(int bit=63-Long.numberOfLeadingZeros(n);bit>=0;bit--){long c=a*((2*b%MOD-a+MOD)%MOD)%MOD,d=(a*a%MOD+b*b%MOD)%MOD;if(((n>>bit)&1)==0){a=c;b=d;}else{a=d;b=(c+d)%MOD;}}return ""+a;',7)

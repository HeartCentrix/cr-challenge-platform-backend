"""Generate 100 authored follow-ups for 20 preserved coding questions. Offline only."""
import json
from pathlib import Path
import random
import sys
sys.dont_write_bytecode=True
import arrays_bank
from common import BANK

# slug, FIB code, answer, buggy fragment, replacement, ordered algorithm steps, explanation prompt, review rubric
SETS=[
('maximum-subarray-sum','cur = Math.___(a[i], cur + a[i]);','max','long best = 0;','long best = a[0];',['Initialize from the first value','Compare extending the current segment with starting again','Track the largest segment sum seen'],'Why must an all-negative array not return zero?','The selected subarray must be nonempty; return the largest (least negative) value.'),
('products-excluding-self','prefix = prefix * a[i] ___ MOD;','%','answer[i] = total / a[i];','answer[i] = prefixBefore[i] * suffixAfter[i] % MOD;',['Accumulate products strictly before each index','Accumulate products strictly after each index','Multiply the two products modulo MOD'],'Why does total-product division fail when zero is present?','Division by zero is invalid. Prefix/suffix products handle one or several zeros without division.'),
('rotate-array-right','int destination = (i + k) ___ n;','%','int destination = (i - k) % n;','int destination = (i + k % n) % n;',['Reduce k modulo the array length','Map each source index to its shifted destination','Print the destination array'],'What should happen when k is a multiple of n?','The rotation is zero modulo n, so every value remains at the same index.'),
('count-array-inversions','if (a[i] ___ a[j]) inversions++;','>','if (a[i] >= a[j]) inversions++;','if (a[i] > a[j]) inversions++;',['Scan positions from right to left','Count already seen values strictly below the current value','Insert the current value into the frequency structure'],'Should equal values contribute inversions? Why?','No. An inversion requires an earlier value to be strictly greater than a later value.'),
('strict-majority','if (count ___ n / 2) return candidate;','>','return candidate;','return count > n / 2 ? String.valueOf(candidate) : "NONE";',['Choose a candidate by cancellation','Count its actual occurrences','Verify the strict majority threshold'],'Why verify the cancellation candidate in a second pass?','Cancellation supplies a candidate even when no majority exists; its count must exceed n/2.'),
('first-missing-positive','if (a[i] != i + 1) return i ___ 1;','+','if (a[i] >= 0 && a[i] <= n) swap(i, a[i] - 1);','if (a[i] > 0 && a[i] <= n && a[a[i] - 1] != a[i]) swap(i, a[i] - 1);',['Place each in-range positive value at index value minus one','Scan for the first mismatched index','Return that index plus one or n plus one'],'Why can values above n be ignored during placement?','The smallest missing positive among n values is at most n+1; only values 1 through n have target slots.'),
('count-target-pairs','answer += seen.getOrDefault(k ___ x, 0L);','-','seen.merge(x, 1L, Long::sum); answer += seen.getOrDefault(k-x, 0L);','answer += seen.getOrDefault(k-x, 0L); seen.merge(x, 1L, Long::sum);',['Start with an empty frequency map','Count earlier complements of the current value','Record the current value for later positions'],'Why count complements before inserting the current value?','To avoid pairing an index with itself, especially when 2*x equals the target.'),
('distinct-zero-sum-triplets','if (sum < 0) left___;','++','if (sum < 0) right--;','if (sum < 0) left++;',['Sort the values','Fix one value and scan the suffix with two pointers','Skip equal values after recording each zero-sum triplet'],'Why skip duplicate fixed values?','Otherwise the same value triplet may be counted repeatedly even though only distinct value triplets count.'),
('leftmost-balance-index','right ___= a[i]; // remove the current value before comparing','-','left += a[i]; if (left == right) return i;','right -= a[i]; if (left == right) return i; left += a[i];',['Compute the sum of the whole array','Remove the current value from the right sum before comparing','Add the current value to the left sum after comparing'],'Are the pivot value and empty sides included in the two sums?','The pivot is excluded; an empty side contributes zero. Return the first valid index.'),
('maximum-product-subarray','long newHigh = Math.max(x, Math.max(high * x, ___ * x));','low','high = Math.max(x, high * x);','high = Math.max(x, Math.max(oldHigh * x, oldLow * x));',['Keep the previous maximum and minimum products','Multiply both by the next value and compare with a fresh start','Update the global maximum'],'Why track the minimum product as well as the maximum?','Multiplying a negative minimum by a negative value can produce the largest positive product.'),
('shortest-target-sum-window','while (sum ___ k) { best = Math.min(best, right-left+1); sum -= a[left++]; }','>=','while (sum > k) shrink();','while (sum >= k) shrink();',['Extend the right edge and add its value','Shrink from the left while the target is still met','Keep the shortest qualifying window length'],'Why does this sliding-window rule depend on nonnegative values?','Adding nonnegative values cannot decrease the sum and removing them cannot increase it; negative values break that monotonicity.'),
('longest-consecutive-values','if (!set.contains(x ___ 1)) { /* start a run */ }','-','for (int x : a) scanRunStartingAt(x);','for (int x : set) if (!set.contains(x - 1)) scanRunStartingAt(x);',['Build a set of distinct values','Start only from values with no predecessor','Walk successors and record the longest run'],'Why should duplicate values not extend a consecutive run?','The run counts distinct consecutive integer values; repeated copies do not introduce a new successor.'),
('next-greater-value','while (!stack.isEmpty() && stack.peek() ___ a[i]) stack.pop();','<=','while (!stack.isEmpty() && stack.peek() < a[i]) stack.pop();','while (!stack.isEmpty() && stack.peek() <= a[i]) stack.pop();',['Scan the array from right to left','Discard stack values no greater than the current value','Read the next greater value then push the current value'],'Why must an equal stack value be removed?','The required next value is strictly greater, not greater than or equal.'),
('warmer-day-distance','answer[previous] = current ___ previous;','-','answer[previous] = temperature[current] - temperature[previous];','answer[previous] = current - previous;',['Keep unresolved day indices in a monotonic stack','Resolve cooler days when a warmer day arrives','Store index differences and leave unresolved days at zero'],'What is the answer for the final day, and why?','Zero, because no later day exists.'),
('single-stock-trade','lowest = Math.___(lowest, price);','min','best = Math.max(best, lowest - price);','best = Math.max(best, price - lowest);',['Track the lowest price seen so far','Evaluate selling at the current price','Keep the largest nonnegative profit'],'Why return zero when prices only decrease?','No profitable buy-before-sell transaction exists and making no trade is allowed.'),
('unlimited-stock-trades','profit += Math.max(0, a[i] ___ a[i-1]);','-','profit += a[i] - a[i-1];','profit += Math.max(0, a[i] - a[i-1]);',['Compare each price with the preceding price','Add only positive changes','Return the accumulated profit'],'Why can adjacent positive changes be added with unlimited fee-free trades?','Each rising run can be captured as a single trade or adjacent trades with the same telescoping total; falling changes are avoided.'),
('maximum-sortable-chunks','if (frequencyDifference.is___()) chunks++;','Empty','if (a[i] == sorted[i]) chunks++;','if (frequencyDifference.isEmpty()) chunks++;',['Create a sorted copy','Track multiset differences between original and sorted prefixes','End a chunk when the prefix difference is empty'],'Why is comparing only the current pair of values insufficient?','A chunk boundary requires whole-prefix multisets to match, not merely one position.'),
('shortest-unsorted-segment','length = right - left ___ 1;','+','return right - left;','return right < 0 ? 0 : right - left + 1;',['Locate values violating the running maximum from the left','Locate values violating the running minimum from the right','Measure the inclusive interval between the boundaries'],'What should an already sorted array return?','Zero; no segment needs sorting.'),
('shortest-degree-segment','length = lastIndex - firstIndex ___ 1;','+','best = Math.max(best, lastIndex - firstIndex + 1);','best = Math.min(best, lastIndex - firstIndex + 1);',['Count frequencies and track first and last positions','Identify values achieving the whole-array degree','Choose the shortest span among those values'],'Why must a qualifying span include every occurrence of a degree-achieving value?','To retain that maximum frequency, all occurrences of at least one value with the global degree must be present.'),
('nearby-equal-values','if (i - previous ___ k) return true;','<=','if (i - previous < k) return true;','if (i - previous <= k) return true;',['Track the most recent index for each value','Compare the current gap against k','Replace the saved index with the current index'],'Why is keeping only the most recent occurrence enough?','It gives the smallest possible distance to an earlier equal value. Older occurrences cannot offer a smaller gap.'),
]

def build():
    byslug={q['slug']:q for q in BANK};out=[]
    assert len(SETS)==20 and len({s[0] for s in SETS})==20
    for slug,fib,blank,bug,fix,steps,short,rubric in SETS:
        q=byslug[slug]
        def add(ordinal,kind,prompt,expected,options=None,review=''):
            out.append(dict(slug=slug,ordinal=ordinal,kind=kind,prompt=prompt,expected=expected,options=options or [],rubric=review))
        add(1,'FIB','Complete the missing Java token in this fragment for '+q['title']+':\n\n'+fib,[blank],review='Required Java token: '+blank)
        stdin,expected=q['tests'][7]
        values=expected.split();wrong=[]
        for delta in [1,-1,2]:
            try: wrong.append(' '.join([str(int(values[0])+delta)]+values[1:]))
            except ValueError: wrong.append(['0','1','-1'][delta if delta>=0 else 0])
        choices=list(dict.fromkeys([expected]+wrong))
        for x in ['NONE','EMPTY','0','-1','1']:
            if len(choices)<4 and x not in choices:choices.append(x)
        random.Random(slug).shuffle(choices)
        options=[dict(id=chr(65+i),text=x) for i,x in enumerate(choices)]
        add(2,'MCQ','What output should a correct solution produce for this input?\n\n'+stdin,[next(o['id'] for o in options if o['text']==expected)],options,'Apply the coding question specification. Expected output: '+expected)
        pieces=[dict(id=str(i+1),text=s) for i,s in enumerate(steps)]
        random.Random(slug+'steps').shuffle(pieces)
        # Opaque IDs deliberately do not reveal the correct order.
        mapping={p['id']:chr(65+i) for i,p in enumerate(pieces)}
        add(3,'DRAG_DROP','Complete the algorithm for '+q['title']+'. Place the three pieces in execution order: [1], [2], [3].',[''.join(mapping[str(i)]) for i in range(1,4)],
            [dict(id=mapping[p['id']],text=p['text']) for p in pieces],'Correct order: '+' -> '.join(steps))
        add(4,'DEBUG','Replace this incorrect Java fragment with a corrected fragment. Keep the same variable names.\n\n'+bug,[fix],review='Suggested correction: '+fix+'. Equivalent corrections should be reviewed by staff.')
        add(5,'SHORT_ANSWER',short,[],review=rubric)
    assert len(out)==100
    data=json.dumps(out,ensure_ascii=True,separators=(',',':'))
    sql='''-- Authored by db/bank200/build_followups.py. Requires migrations 11 and 12.
BEGIN;
DO $$ BEGIN IF current_database()<>'challenge_platform' THEN RAISE EXCEPTION 'Wrong database'; END IF; END $$;
CREATE TEMP TABLE followup_seed ON COMMIT DROP AS
SELECT * FROM jsonb_to_recordset($followups$'''+data+'''$followups$::jsonb)
AS f(slug text,ordinal int,kind text,prompt text,options jsonb,expected jsonb,rubric text);
DO $$ BEGIN
 IF (SELECT count(*) FROM followup_seed f JOIN challenge_platform.question q USING(slug))<>100 THEN RAISE EXCEPTION 'Missing coding questions'; END IF;
END $$;
INSERT INTO challenge_platform.question_followup(question_id,ordinal,kind,prompt,options,expected_answer,rubric)
SELECT q.id,f.ordinal,f.kind,f.prompt,f.options,f.expected,f.rubric FROM followup_seed f JOIN challenge_platform.question q USING(slug)
ON CONFLICT(question_id,ordinal) DO NOTHING;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM followup_seed f JOIN challenge_platform.question q USING(slug)
 JOIN challenge_platform.question_followup v ON v.question_id=q.id AND v.ordinal=f.ordinal
 WHERE (v.kind,v.prompt,v.options,v.expected_answer,v.rubric,v.enabled) IS DISTINCT FROM (f.kind,f.prompt,f.options,f.expected,f.rubric,true)) THEN
 RAISE EXCEPTION 'Existing follow-ups differ; refusing to overwrite'; END IF;
 IF (SELECT count(*) FROM challenge_platform.followup_eligible_question)<>20 THEN RAISE EXCEPTION 'Expected twenty eligible questions'; END IF;
END $$;
COMMIT;
'''
    (Path(__file__).resolve().parent.parent/'13_seed_question_followups.sql').write_text(sql,encoding='utf-8',newline='\n')
    print('Generated 20 complete sets / 100 follow-ups; no database changes.')

if __name__=='__main__':build()

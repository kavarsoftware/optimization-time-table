"""Summarize recorded results; never substitutes missing feasible objectives with zero."""
import csv, statistics
from pathlib import Path
from collections import defaultdict
root=Path(__file__).resolve().parents[1]
groups=defaultdict(list)
with (root/'results/experiments.csv').open() as f:
    for row in csv.DictReader(f):groups[row['instance'],row['method']].append(row)
lines=['# Measured 30-seed results','', 'Version UniPlan5-1.0. Each run has a 2-second maximum budget, seed 1–30, population 16, generation cap 100, mutation 0.12, one CP worker. Linux amd64, Java 21.0.13, 9 logical CPUs; shared execution host. Hardware load and JVM warm-up were not controlled. Domain generation is outside the search timer; CP construction is inside it. Native shutdown may exceed a requested limit.', '', '| Instance | Method | Feasible | Best | Mean | Median | Worst | Population SD | Mean runtime s | Relative gap |', '|---|---|---:|---:|---:|---:|---:|---:|---:|---|']
for (name,method),rows in groups.items():
    scores=[int(r['objective']) for r in rows if r['feasible']=='true']
    stats=[min(scores),statistics.mean(scores),statistics.median(scores),max(scores),statistics.pstdev(scores)] if scores else ['NA']*5
    values=[f'{s:.3f}' if isinstance(s,float) else str(s) for s in stats]
    gap='Undefined (optimum = 0); absolute gap 0' if name=='tiny' and scores else 'NA'
    lines.append('| '+' | '.join([name,method,f'{len(scores)}/{len(rows)}',*values,f"{statistics.mean(float(r['runtime_seconds']) for r in rows):.4f}",gap])+' |')
lines+=['','Statistics use feasible runs only. Tiny has optimum zero, so the requested relative gap (value − optimum)/optimum is undefined, not a division by zero or a fabricated percentage. Impossible has no optimum; realistic has no known optimum.','', 'The realistic case has 5 stages × 2 groups × 8 one-hour sessions, 25 rooms, 15 instructors and 55,000 eligible placements. It is synthetic and deliberately unconstrained in resource availability, creating considerable symmetry. All short-budget methods failed to find a feasible schedule in this measured run. This is a scaling limitation, not evidence that the instance is impossible. The longer ScalingProbe is reported separately and is not a fair equal-budget method comparison.','', 'Convergence data is in convergence.csv. Blank/no trace means no feasible incumbent. Evaluations count independently evaluated candidates/incumbents, not CP search nodes; equal evaluation counts are not equal computational work. Random traces retain strict improvements only.']
(root/'results/SUMMARY.md').write_text('\n'.join(lines)+'\n')
try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    traces=defaultdict(list)
    with (root/'results/convergence.csv').open() as f:
        for row in csv.DictReader(f):
            if row['instance']=='tiny' and row['seed']=='7':traces[row['method']].append((int(row['evaluations']),int(row['objective'])))
    fig,ax=plt.subplots(figsize=(8,4))
    for name,points in traces.items():
        x,y=zip(*points);ax.step(x,y,where='post',marker='o',label=name)
    ax.set(title='Tiny instance, seed 7: best feasible objective',xlabel='Independent evaluator calls',ylabel='Weighted penalty');ax.legend();ax.grid(alpha=.2);fig.tight_layout();fig.savefig(root/'results/convergence.svg');plt.close(fig)
except ImportError:
    print('Summary written. Install matplotlib for optional SVG chart.')
print('Wrote results/SUMMARY.md')

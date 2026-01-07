# SUCCESS CRITERIA - READ THIS FIRST

## NEVER SAY "FINAL"

This is a MONTH-LONG competition (Jan 7 - Jan 31).
- ❌ Don't say "final submission", "final version", "final test"
- ✅ Say "current submission", "latest version", "test results"

We're on DAY 2. This is the BEGINNING.

---

## WHAT MATTERS:

### ✅ SUCCESS = King survives by getting cheese deliveries
- Positive cheese income (deliveries > consumption)
- Regular TRANSFER messages in logs
- King cheese level stable or increasing
- Death from enemy attack (not starvation)

### ❌ FAILURE = King starves to death
- No cheese deliveries reaching king
- Negative cheese income
- King cheese dropping to 0
- Death from starvation

## WHAT DOESN'T MATTER:

- ❌ How many rounds we survived
- ❌ Whether we "won" the match
- ❌ Round count comparisons
- ❌ Beating examplefuncsplayer

## HOW TO MEASURE SUCCESS:

1. Run match
2. Check FINAL 50 rounds before king death
3. Look for TRANSFER messages
4. Check king cheese trend (increasing or decreasing?)
5. Determine cause of death: starvation or combat

## TRAFFIC JAM = STARVATION = FAILURE

If rats have cheese but can't deliver it → TRAFFIC JAM → STARVATION → FAILURE

The problem is NOT SOLVED until:
- Rats carrying cheese can REACH the king
- TRANSFER messages happen regularly
- King cheese stays above 100
- King dies from combat, NOT starvation

---

**STOP celebrating round counts. START analyzing starvation causes.**

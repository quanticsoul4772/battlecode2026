#!/usr/bin/env python3
"""
Battlecode 2026 Log Parser

Parses structured logs from match replays and generates statistics.

Log Format: {CATEGORY}:{round}:{type}:{id}:{key1}={value1}:...

Categories:
- STATE: Robot state snapshots
- ECONOMY: Team economics
- SPAWN: Unit creation
- COMBAT: Attack events
- CAT: Cat tracking
- BACKSTAB: Game state transitions
- CHEESE: Cheese collection/transfer
- TACTICAL: Decision context
- PROFILE: Bytecode usage
"""

import sys
import re
from collections import defaultdict
from dataclasses import dataclass
from typing import Dict, List, Optional

@dataclass
class StateLog:
    round: int
    unit_type: str
    id: int
    x: int
    y: int
    facing: str
    hp: int
    raw_cheese: int
    mode: str

@dataclass
class EconomyLog:
    round: int
    global_cheese: int
    cheese_income: int
    kings: int
    baby_rats: int
    transferred: int

@dataclass
class CombatLog:
    round: int
    attacker_type: str
    attacker_id: int
    from_x: int
    from_y: int
    target_x: int
    target_y: int
    damage: int
    cheese_spent: int
    target_hp: int

@dataclass
class ProfileLog:
    round: int
    id: int
    section: str
    bytecodes: int

class LogParser:
    def __init__(self, log_file: str):
        self.log_file = log_file
        self.state_logs: List[StateLog] = []
        self.economy_logs: List[EconomyLog] = []
        self.combat_logs: List[CombatLog] = []
        self.profile_logs: List[ProfileLog] = []
        self.backstab_round: Optional[int] = None

    def parse(self):
        """Parse log file and extract structured data."""
        with open(self.log_file, 'r') as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue

                parts = line.split(':')
                if len(parts) < 3:
                    continue

                category = parts[0]

                if category == 'STATE':
                    self._parse_state(parts)
                elif category == 'ECONOMY':
                    self._parse_economy(parts)
                elif category == 'COMBAT':
                    self._parse_combat(parts)
                elif category == 'PROFILE':
                    self._parse_profile(parts)
                elif category == 'BACKSTAB':
                    self._parse_backstab(parts)

    def _parse_state(self, parts):
        """Parse STATE log line."""
        try:
            round_num = int(parts[1])
            unit_type = parts[2]
            unit_id = int(parts[3])

            # Parse key-value pairs
            kv = self._parse_kv_pairs(parts[4:])

            pos = kv.get('pos', '[0,0]')
            x, y = self._parse_position(pos)

            self.state_logs.append(StateLog(
                round=round_num,
                unit_type=unit_type,
                id=unit_id,
                x=x, y=y,
                facing=kv.get('facing', 'NORTH'),
                hp=int(kv.get('hp', 100)),
                raw_cheese=int(kv.get('rawCheese', 0)),
                mode=kv.get('mode', 'UNKNOWN')
            ))
        except (ValueError, IndexError):
            pass  # Skip malformed lines

    def _parse_economy(self, parts):
        """Parse ECONOMY log line."""
        try:
            round_num = int(parts[1])
            kv = self._parse_kv_pairs(parts[2:])

            self.economy_logs.append(EconomyLog(
                round=round_num,
                global_cheese=int(kv.get('globalCheese', 0)),
                cheese_income=int(kv.get('cheeseIncome', 0)),
                kings=int(kv.get('kings', 0)),
                baby_rats=int(kv.get('babyRats', 0)),
                transferred=int(kv.get('transferred', 0))
            ))
        except (ValueError, IndexError):
            pass

    def _parse_combat(self, parts):
        """Parse COMBAT log line."""
        try:
            round_num = int(parts[1])
            attacker_type = parts[2]
            attacker_id = int(parts[3])

            kv = self._parse_kv_pairs(parts[4:])

            from_pos = kv.get('from', '[0,0]')
            from_x, from_y = self._parse_position(from_pos)

            target_pos = kv.get('target', '[0,0]')
            target_x, target_y = self._parse_position(target_pos)

            self.combat_logs.append(CombatLog(
                round=round_num,
                attacker_type=attacker_type,
                attacker_id=attacker_id,
                from_x=from_x, from_y=from_y,
                target_x=target_x, target_y=target_y,
                damage=int(kv.get('damage', 0)),
                cheese_spent=int(kv.get('cheeseSpent', 0)),
                target_hp=int(kv.get('targetHP', 0))
            ))
        except (ValueError, IndexError):
            pass

    def _parse_profile(self, parts):
        """Parse PROFILE log line."""
        try:
            round_num = int(parts[1])
            unit_id = int(parts[2])
            section = parts[3]
            bytecodes = int(parts[4])

            self.profile_logs.append(ProfileLog(
                round=round_num,
                id=unit_id,
                section=section,
                bytecodes=bytecodes
            ))
        except (ValueError, IndexError):
            pass

    def _parse_backstab(self, parts):
        """Parse BACKSTAB log line."""
        try:
            self.backstab_round = int(parts[1])
        except (ValueError, IndexError):
            pass

    def _parse_kv_pairs(self, parts):
        """Parse key=value pairs from log parts."""
        kv = {}
        for part in parts:
            if '=' in part:
                key, value = part.split('=', 1)
                kv[key] = value
        return kv

    def _parse_position(self, pos_str):
        """Parse position string [x,y] to (x, y)."""
        match = re.search(r'\[(\d+),(\d+)\]', pos_str)
        if match:
            return int(match.group(1)), int(match.group(2))
        return 0, 0

    def analyze(self):
        """Generate analysis report."""
        report = []

        report.append("=== Match Analysis ===\n")

        # Economy analysis
        if self.economy_logs:
            final_eco = self.economy_logs[-1]
            report.append(f"Final Economy (Round {final_eco.round}):")
            report.append(f"  Global Cheese: {final_eco.global_cheese}")
            report.append(f"  Kings: {final_eco.kings}")
            report.append(f"  Baby Rats: {final_eco.baby_rats}")
            report.append(f"  Cheese Transferred: {final_eco.transferred}")

            # Calculate cheese trend
            if len(self.economy_logs) > 10:
                early = self.economy_logs[5].global_cheese
                late = final_eco.global_cheese
                trend = "Growing" if late > early else "Declining"
                report.append(f"  Cheese Trend: {trend} ({early} -> {late})")

            report.append("")

        # Combat statistics
        if self.combat_logs:
            total_damage = sum(c.damage for c in self.combat_logs)
            total_cheese_spent = sum(c.cheese_spent for c in self.combat_logs)
            attacks = len(self.combat_logs)

            report.append(f"Combat Statistics:")
            report.append(f"  Total Attacks: {attacks}")
            report.append(f"  Total Damage: {total_damage}")
            report.append(f"  Cheese Spent on Bites: {total_cheese_spent}")
            report.append(f"  Average Damage/Attack: {total_damage / attacks:.1f}")

            if total_cheese_spent > 0:
                report.append(f"  Damage/Cheese: {total_damage / total_cheese_spent:.2f}")

            report.append("")

        # Bytecode profiling
        if self.profile_logs:
            # Aggregate by section
            section_totals = defaultdict(int)
            section_counts = defaultdict(int)

            for prof in self.profile_logs:
                section_totals[prof.section] += prof.bytecodes
                section_counts[prof.section] += 1

            report.append("Bytecode Usage by Section:")
            for section in sorted(section_totals.keys()):
                total = section_totals[section]
                count = section_counts[section]
                avg = total / count
                report.append(f"  {section:20s}: {avg:7.0f} avg ({count} samples)")

            report.append("")

        # Backstab analysis
        if self.backstab_round:
            report.append(f"Backstab Triggered: Round {self.backstab_round}")
            report.append("")

        # Robot survival analysis
        if self.state_logs:
            # Track unique robot IDs per round
            rounds_with_counts = defaultdict(set)
            for state in self.state_logs:
                rounds_with_counts[state.round].add(state.id)

            if rounds_with_counts:
                final_round = max(rounds_with_counts.keys())
                final_count = len(rounds_with_counts[final_round])
                report.append(f"Unit Survival:")
                report.append(f"  Final Round: {final_round}")
                report.append(f"  Units Alive: {final_count}")

                # Show trend
                if len(rounds_with_counts) > 10:
                    mid_round = final_round // 2
                    mid_count = len(rounds_with_counts.get(mid_round, set()))
                    report.append(f"  Mid-game Units: {mid_count}")
                    report.append(f"  Survival Rate: {final_count / max(mid_count, 1):.2f}x")

        return '\n'.join(report)

    def export_csv(self, output_file: str):
        """Export parsed data to CSV for analysis."""
        import csv

        with open(output_file, 'w', newline='') as f:
            writer = csv.writer(f)

            # Economy data
            writer.writerow(['ECONOMY'])
            writer.writerow(['Round', 'GlobalCheese', 'CheeseIncome', 'Kings', 'BabyRats', 'Transferred'])
            for eco in self.economy_logs:
                writer.writerow([eco.round, eco.global_cheese, eco.cheese_income,
                               eco.kings, eco.baby_rats, eco.transferred])

            writer.writerow([])

            # Combat data
            writer.writerow(['COMBAT'])
            writer.writerow(['Round', 'AttackerType', 'Damage', 'CheeseSpent', 'TargetHP'])
            for combat in self.combat_logs:
                writer.writerow([combat.round, combat.attacker_type, combat.damage,
                               combat.cheese_spent, combat.target_hp])

def main():
    if len(sys.argv) < 2:
        print("Usage: python log_parser.py <log_file>")
        sys.exit(1)

    log_file = sys.argv[1]

    parser = LogParser(log_file)
    parser.parse()

    print(parser.analyze())

    # Export CSV if requested
    if len(sys.argv) > 2:
        parser.export_csv(sys.argv[2])
        print(f"\nExported to {sys.argv[2]}")

if __name__ == '__main__':
    main()

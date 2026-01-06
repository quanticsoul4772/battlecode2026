#!/usr/bin/env python3
"""
Battlecode 2026 Performance Dashboard

Generates visual performance metrics from match logs.

Tracks:
- Cheese income over time
- King health trends
- Bytecode usage patterns
- Combat effectiveness
- Unit count evolution
"""

import sys
import matplotlib.pyplot as plt
from log_parser import LogParser

class PerformanceDashboard:
    def __init__(self, log_file: str):
        self.parser = LogParser(log_file)
        self.parser.parse()

    def plot_cheese_economy(self):
        """Plot cheese over time."""
        if not self.parser.economy_logs:
            return

        rounds = [e.round for e in self.parser.economy_logs]
        cheese = [e.global_cheese for e in self.parser.economy_logs]

        plt.figure(figsize=(12, 6))
        plt.plot(rounds, cheese, 'g-', linewidth=2, label='Global Cheese')
        plt.axhline(y=0, color='r', linestyle='--', label='Starvation Risk')
        plt.xlabel('Round')
        plt.ylabel('Global Cheese')
        plt.title('Cheese Economy Over Time')
        plt.legend()
        plt.grid(True, alpha=0.3)

        return plt.gcf()

    def plot_unit_counts(self):
        """Plot unit counts over time."""
        if not self.parser.economy_logs:
            return

        rounds = [e.round for e in self.parser.economy_logs]
        kings = [e.kings for e in self.parser.economy_logs]
        rats = [e.baby_rats for e in self.parser.economy_logs]

        plt.figure(figsize=(12, 6))
        plt.plot(rounds, kings, 'r-', linewidth=2, marker='o', label='Rat Kings')
        plt.plot(rounds, rats, 'b-', linewidth=2, marker='s', markersize=3, label='Baby Rats')
        plt.xlabel('Round')
        plt.ylabel('Unit Count')
        plt.title('Unit Population Over Time')
        plt.legend()
        plt.grid(True, alpha=0.3)

        return plt.gcf()

    def plot_bytecode_usage(self):
        """Plot bytecode usage by section."""
        if not self.parser.profile_logs:
            return

        from collections import defaultdict

        section_avg = defaultdict(list)

        for prof in self.parser.profile_logs:
            section_avg[prof.section].append(prof.bytecodes)

        # Calculate averages
        sections = []
        averages = []

        for section, values in section_avg.items():
            if not section.endswith('_TOTAL'):  # Skip summary entries
                sections.append(section)
                averages.append(sum(values) / len(values))

        # Sort by average
        sorted_data = sorted(zip(sections, averages), key=lambda x: x[1], reverse=True)
        sections, averages = zip(*sorted_data) if sorted_data else ([], [])

        plt.figure(figsize=(12, 8))
        plt.barh(sections, averages, color='steelblue')
        plt.xlabel('Average Bytecodes')
        plt.ylabel('Code Section')
        plt.title('Bytecode Usage by Section')
        plt.axvline(x=17500, color='r', linestyle='--', label='Baby Rat Limit')
        plt.axvline(x=20000, color='orange', linestyle='--', label='Rat King Limit')
        plt.legend()
        plt.grid(True, alpha=0.3, axis='x')
        plt.tight_layout()

        return plt.gcf()

    def plot_combat_damage(self):
        """Plot cumulative combat damage over time."""
        if not self.parser.combat_logs:
            return

        rounds = []
        cumulative_damage = []
        total = 0

        for combat in sorted(self.parser.combat_logs, key=lambda c: c.round):
            rounds.append(combat.round)
            total += combat.damage
            cumulative_damage.append(total)

        plt.figure(figsize=(12, 6))
        plt.plot(rounds, cumulative_damage, 'r-', linewidth=2)
        plt.xlabel('Round')
        plt.ylabel('Cumulative Damage')
        plt.title('Total Damage Dealt Over Time')
        plt.grid(True, alpha=0.3)

        # Mark cat kill threshold (10,000 HP)
        plt.axhline(y=10000, color='purple', linestyle='--', label='Cat HP (10,000)')
        plt.legend()

        return plt.gcf()

    def generate_dashboard(self, output_prefix: str):
        """Generate all plots and save to files."""
        plots = [
            ('cheese_economy', self.plot_cheese_economy),
            ('unit_counts', self.plot_unit_counts),
            ('bytecode_usage', self.plot_bytecode_usage),
            ('combat_damage', self.plot_combat_damage),
        ]

        generated = []

        for name, plot_func in plots:
            try:
                fig = plot_func()
                if fig:
                    filename = f"{output_prefix}_{name}.png"
                    fig.savefig(filename, dpi=150, bbox_inches='tight')
                    generated.append(filename)
                    plt.close(fig)
                    print(f"Generated: {filename}")
            except Exception as e:
                print(f"Warning: Could not generate {name}: {e}")

        return generated

def main():
    if len(sys.argv) < 2:
        print("Usage: python performance_dashboard.py <log_file> [output_prefix]")
        print("\nGenerates performance visualization plots:")
        print("  - Cheese economy over time")
        print("  - Unit count trends")
        print("  - Bytecode usage breakdown")
        print("  - Combat damage progression")
        sys.exit(1)

    log_file = sys.argv[1]
    output_prefix = sys.argv[2] if len(sys.argv) > 2 else "dashboard"

    dashboard = PerformanceDashboard(log_file)
    plots = dashboard.generate_dashboard(output_prefix)

    print(f"\nGenerated {len(plots)} plots")
    print("\nAnalysis:")
    print(dashboard.parser.analyze())

if __name__ == '__main__':
    main()

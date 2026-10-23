import { Component } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

interface Summary {
  label: string;
  amount: number;
  kind: 'balance' | 'income' | 'expense' | 'savings';
  icon: string;
  movement: string;
  isUp: boolean;
}

interface Transaction {
  date: string;
  description: string;
  category: string;
  icon: string;
  amount: number;
}

interface Category {
  label: string;
  percent: number;
  color: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, DecimalPipe, MatCardModule, MatIconModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard {
  // Mock financial figures only; replace with Spring Boot API data later.
  readonly currencyCode = 'PHP';

  readonly summaries: Summary[] = [
    { label: 'Total Balance', amount: 125430.50, kind: 'balance', icon: 'account_balance_wallet', movement: '12% from last month', isUp: true },
    { label: 'Income', amount: 45200.00, kind: 'income', icon: 'trending_up', movement: '8% from last month', isUp: true },
    { label: 'Expenses', amount: 28450.75, kind: 'expense', icon: 'shopping_cart', movement: '5% from last month', isUp: false },
    { label: 'Savings', amount: 16749.25, kind: 'savings', icon: 'savings', movement: '18% from last month', isUp: true }
  ];

  readonly months = [
    { name: 'Jan', income: 30000, expenses: 19000 },
    { name: 'Feb', income: 39000, expenses: 16000 },
    { name: 'Mar', income: 35500, expenses: 24000 },
    { name: 'Apr', income: 40000, expenses: 21000 },
    { name: 'May', income: 47000, expenses: 27000 },
    { name: 'Jun', income: 55000, expenses: 33000 }
  ];
  readonly chartMax = 60000;
  readonly chartTicks = [60000, 40000, 20000, 0];

  readonly expenses: Category[] = [
    { label: 'Food', percent: 35, color: '#3b82f6' },
    { label: 'Transportation', percent: 20, color: '#14b8a6' },
    { label: 'Shopping', percent: 15, color: '#fbbf24' },
    { label: 'Bills & Utilities', percent: 15, color: '#f97316' },
    { label: 'Others', percent: 15, color: '#94a3b8' }
  ];

  readonly transactions: Transaction[] = [
    { date: '2026-10-16', description: 'Grocery Shopping', category: 'Food', icon: 'restaurant', amount: -2350 },
    { date: '2026-10-15', description: 'Salary', category: 'Income', icon: 'payments', amount: 25000 },
    { date: '2026-10-14', description: 'Electric Bill', category: 'Utilities', icon: 'bolt', amount: -3200 },
    { date: '2026-10-13', description: 'Taxi Fare', category: 'Transport', icon: 'directions_car', amount: -650 }
  ];

  readonly budgets = [
    { label: 'Food', percent: 70, tone: 'teal' },
    { label: 'Transportation', percent: 45, tone: 'blue' },
    { label: 'Entertainment', percent: 30, tone: 'yellow' }
  ];
}

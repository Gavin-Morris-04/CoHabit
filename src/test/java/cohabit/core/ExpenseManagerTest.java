package cohabit.core;

import cohabit.firebase.FirebaseService;
import cohabit.model.Expense;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ExpenseManagerTest {
    @Test
    void evenSplitCreatesEqualPercentages() throws Exception {
        InMemoryGateway gateway = new InMemoryGateway();
        FirebaseService service = new FirebaseService(gateway, gateway);
        ExpenseManager expenseManager = new ExpenseManager(service);

        Expense expense = expenseManager.addExpense(
                "room-1",
                "Groceries",
                120.0,
                "u1",
                true,
                List.of("u1", "u2", "u3"),
                true,
                Map.of()
        );
        Assertions.assertEquals(3, expense.getCustomSplitPercentages().size());
        Assertions.assertEquals(33.333333333333336, expense.getCustomSplitPercentages().get("u1"));
    }

    @Test
    void customSplitAssignsRemainderToPayer() throws Exception {
        InMemoryGateway gateway = new InMemoryGateway();
        FirebaseService service = new FirebaseService(gateway, gateway);
        ExpenseManager expenseManager = new ExpenseManager(service);

        Expense expense = expenseManager.addExpense(
                "room-1",
                "Utilities",
                100.0,
                "u1",
                true,
                List.of("u1", "u2"),
                false,
                Map.of("u1", 70.0, "u2", 20.0)
        );
        Assertions.assertEquals(80.0, expense.getCustomSplitPercentages().get("u1"));
        Assertions.assertEquals(20.0, expense.getCustomSplitPercentages().get("u2"));
        Assertions.assertEquals(100.0, expense.getCustomSplitPercentages().values().stream().mapToDouble(Double::doubleValue).sum(), 0.0001);
    }

    @Test
    void balanceCalculationTracksWhoOwesWhom() {
        InMemoryGateway gateway = new InMemoryGateway();
        FirebaseService service = new FirebaseService(gateway, gateway);
        ExpenseManager expenseManager = new ExpenseManager(service);

        Expense e1 = new Expense();
        e1.setPaidByUserID("u1");
        e1.setAmount(100.0);
        e1.setPaid(true);
        e1.setCustomSplitPercentages(Map.of("u1", 50.0, "u2", 50.0));
        Expense e2 = new Expense();
        e2.setPaidByUserID("u2");
        e2.setAmount(60.0);
        e2.setPaid(true);
        e2.setCustomSplitPercentages(Map.of("u1", 50.0, "u2", 50.0));

        Map<String, Double> balances = expenseManager.calculateNetBalances(List.of(e1, e2), List.of("u1", "u2"));
        Assertions.assertEquals(20.0, balances.get("u1"));
        Assertions.assertEquals(-20.0, balances.get("u2"));
    }
}

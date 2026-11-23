package com.kosh.ledger.invariant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component 
public class InvariantChecker {

    private final JdbcTemplate jdbc;

    public InvariantChecker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> check() {
        List<String> problems = new ArrayList<>();

        // All balances sum to 0.
        Long total = jdbc.queryForObject("SELECT COALESCE(SUM(posted),0) FROM BALANCE", Long.class);
        if (total != 0) {
            problems.add("All balances sum to " + total + ". Expected 0.");
        }

        // Every balance equals the sum of its postings
        List<Map<String,Object>> wrongBalances = jdbc.queryForList("""
            SELECT b.account_id, b.posted, COALESCE(SUM(p.amount),0) AS postings_sum
            FROM Balance b LEFT JOIN Posting p ON b.account_id = p.account_id
            GROUP BY b.account_id, b.posted
            HAVING b.posted <> COALESCE(SUM(p.amount),0)""");
        for (Map<String, Object> row: wrongBalances) {
            problems.add("Account " + row.get("account_id") + " has posted " + row.get("posted") + " but its postings sum to " + row.get("postings_sum"));
        }

        // Every entry has atleast 2 postings summing to 0
        List<Map<String, Object>> badEntries = jdbc.queryForList("""
            SELECT e.id, COUNT(p.id) AS postings, COALESCE(SUM(p.amount), 0) AS total
            FROM journal_entry e LEFT JOIN posting p ON p.entry_id = e.id
            GROUP BY e.id
            HAVING COUNT(p.id) < 2 OR COALESCE(SUM(p.amount), 0) <> 0
            """);
        for (Map<String, Object> row : badEntries) {
            problems.add("Entry " + row.get("id") + " has " + row.get("postings") + " postings summing to " + row.get("total"));
        }

        return problems;
    }
    
}

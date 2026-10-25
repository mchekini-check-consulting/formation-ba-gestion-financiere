package com.example.gestionfinanciere.logging;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Journalise layer=db uniquement les requêtes en erreur ou plus lentes que
 * SLOW_QUERY_THRESHOLD_MS. Ne capture jamais les valeurs liées aux paramètres :
 * seul le texte SQL (déjà paramétré, donc sans valeurs) passé à prepareStatement
 * est conservé.
 */
public class SlowQueryLoggingDataSource extends DelegatingDataSource {

	private static final Logger log = LoggerFactory.getLogger(SlowQueryLoggingDataSource.class);
	private static final long SLOW_QUERY_THRESHOLD_MS = 200;

	public SlowQueryLoggingDataSource(DataSource targetDataSource) {
		super(targetDataSource);
	}

	@Override
	public Connection getConnection() throws SQLException {
		return wrap(super.getConnection());
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return wrap(super.getConnection(username, password));
	}

	private static Connection wrap(Connection connection) {
		return (Connection) Proxy.newProxyInstance(
				Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class },
				new ConnectionHandler(connection));
	}

	private static final class ConnectionHandler implements InvocationHandler {

		private final Connection delegate;

		private ConnectionHandler(Connection delegate) {
			this.delegate = delegate;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Object result = invokeReal(delegate, method, args);
			if ("prepareStatement".equals(method.getName()) && result instanceof PreparedStatement statement) {
				return wrapStatement(statement, (String) args[0]);
			}
			if ("createStatement".equals(method.getName()) && result instanceof Statement statement) {
				return wrapStatement(statement, null);
			}
			return result;
		}

	}

	private static Statement wrapStatement(Statement statement, String preparedSql) {
		Class<?>[] interfaces = statement instanceof PreparedStatement
				? new Class<?>[] { PreparedStatement.class }
				: new Class<?>[] { Statement.class };
		return (Statement) Proxy.newProxyInstance(
				Statement.class.getClassLoader(), interfaces, new StatementHandler(statement, preparedSql));
	}

	private static final class StatementHandler implements InvocationHandler {

		private static final Set<String> EXECUTE_METHODS =
				Set.of("execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch");

		private final Statement delegate;
		private final String preparedSql;

		private StatementHandler(Statement delegate, String preparedSql) {
			this.delegate = delegate;
			this.preparedSql = preparedSql;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (!EXECUTE_METHODS.contains(method.getName())) {
				return invokeReal(delegate, method, args);
			}
			String sql = preparedSql != null ? preparedSql : literalSqlArgument(args);
			long start = System.nanoTime();
			try {
				Object result = invokeReal(delegate, method, args);
				logIfNeeded(sql, Duration.ofNanos(System.nanoTime() - start), null);
				return result;
			} catch (SQLException ex) {
				logIfNeeded(sql, Duration.ofNanos(System.nanoTime() - start), ex);
				throw ex;
			}
		}

		private static String literalSqlArgument(Object[] args) {
			return args != null && args.length > 0 && args[0] instanceof String sql ? sql : null;
		}

	}

	private static void logIfNeeded(String sql, Duration duration, SQLException error) {
		long durationMs = duration.toMillis();
		if (error == null && durationMs < SLOW_QUERY_THRESHOLD_MS) {
			return;
		}
		MdcContext ctx = MdcContext.open().with("layer", "db").with("durationMs", String.valueOf(durationMs));
		if (sql != null) {
			ctx.with("sql", sql);
		}
		if (error != null) {
			ctx.with("err", error.getClass().getSimpleName() + ": " + error.getMessage());
		}
		try (ctx) {
			log.warn(error != null ? "db query failed" : "slow db query");
		}
	}

	private static Object invokeReal(Object target, Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

}

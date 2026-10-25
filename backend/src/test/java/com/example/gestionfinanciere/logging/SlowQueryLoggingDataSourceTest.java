package com.example.gestionfinanciere.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class SlowQueryLoggingDataSourceTest {

	private static final String SQL = "select 1 from item where id = ?";

	private final Logger logger = (Logger) LoggerFactory.getLogger(SlowQueryLoggingDataSource.class);
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@AfterEach
	void detach() {
		logger.detachAppender(appender);
	}

	@Test
	void neJournalisePasUneRequeteRapide() throws SQLException {
		appender.start();
		logger.addAppender(appender);
		Connection connection = openConnectionReturning(mock(ResultSet.class), null);

		connection.prepareStatement(SQL).executeQuery();

		assertTrue(appender.list.isEmpty());
	}

	@Test
	void journaliseUneRequeteLenteSansLesParametres() throws SQLException {
		appender.start();
		logger.addAppender(appender);
		ResultSet resultSet = mock(ResultSet.class);
		Connection connection = openConnectionReturning(resultSet, Duration.ofMillis(210));

		connection.prepareStatement(SQL).executeQuery();

		ILoggingEvent event = appender.list.get(0);
		assertEquals("db", event.getMDCPropertyMap().get("layer"));
		assertEquals(SQL, event.getMDCPropertyMap().get("sql"));
		assertTrue(Long.parseLong(event.getMDCPropertyMap().get("durationMs")) >= 200);
	}

	@Test
	void journaliseEtPropageUneErreur() throws SQLException {
		appender.start();
		logger.addAppender(appender);
		DataSource real = mock(DataSource.class);
		Connection realConnection = mock(Connection.class);
		PreparedStatement realStatement = mock(PreparedStatement.class);
		when(real.getConnection()).thenReturn(realConnection);
		when(realConnection.prepareStatement(SQL)).thenReturn(realStatement);
		when(realStatement.executeQuery()).thenThrow(new SQLException("boom"));

		Connection connection = new SlowQueryLoggingDataSource(real).getConnection();
		PreparedStatement statement = connection.prepareStatement(SQL);

		assertThrows(SQLException.class, statement::executeQuery);

		ILoggingEvent event = appender.list.get(0);
		assertEquals("db", event.getMDCPropertyMap().get("layer"));
		assertEquals(SQL, event.getMDCPropertyMap().get("sql"));
		assertTrue(event.getMDCPropertyMap().get("err").contains("boom"));
	}

	@Test
	void journaliseUneRequeteLenteViaCreateStatement() throws SQLException {
		appender.start();
		logger.addAppender(appender);
		DataSource real = mock(DataSource.class);
		Connection realConnection = mock(Connection.class);
		Statement realStatement = mock(Statement.class);
		when(real.getConnection()).thenReturn(realConnection);
		when(realConnection.createStatement()).thenReturn(realStatement);
		when(realStatement.executeQuery(SQL)).thenAnswer(invocation -> {
			Thread.sleep(210);
			return mock(ResultSet.class);
		});

		Connection connection = new SlowQueryLoggingDataSource(real).getConnection();
		connection.createStatement().executeQuery(SQL);

		ILoggingEvent event = appender.list.get(0);
		assertEquals(SQL, event.getMDCPropertyMap().get("sql"));
	}

	@Test
	void delegueLesMethodesSansExecutionDeRequete() throws SQLException {
		appender.start();
		logger.addAppender(appender);
		DataSource real = mock(DataSource.class);
		Connection realConnection = mock(Connection.class);
		PreparedStatement realStatement = mock(PreparedStatement.class);
		when(real.getConnection()).thenReturn(realConnection);
		when(realConnection.prepareStatement(SQL)).thenReturn(realStatement);
		when(realStatement.getMaxRows()).thenReturn(42);
		when(realConnection.isClosed()).thenReturn(false);

		Connection connection = new SlowQueryLoggingDataSource(real).getConnection();
		PreparedStatement statement = connection.prepareStatement(SQL);

		assertEquals(42, statement.getMaxRows());
		assertEquals(false, connection.isClosed());
		assertTrue(appender.list.isEmpty());
	}

	private Connection openConnectionReturning(ResultSet resultSet, Duration delay) throws SQLException {
		DataSource real = mock(DataSource.class);
		Connection realConnection = mock(Connection.class);
		PreparedStatement realStatement = mock(PreparedStatement.class);
		when(real.getConnection()).thenReturn(realConnection);
		when(realConnection.prepareStatement(SQL)).thenReturn(realStatement);
		when(realStatement.executeQuery()).thenAnswer(invocation -> {
			if (delay != null) {
				Thread.sleep(delay.toMillis());
			}
			return resultSet;
		});
		return new SlowQueryLoggingDataSource(real).getConnection();
	}

}

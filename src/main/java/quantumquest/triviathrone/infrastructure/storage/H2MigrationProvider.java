package quantumquest.triviathrone.infrastructure.storage;

import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import quantumquest.triviathrone.api.storage.migration.IMigration;
import quantumquest.triviathrone.api.storage.migration.IMigrationProvider;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class H2MigrationProvider implements IMigrationProvider {
    private static final String MIGRATION_DIRECTORY = "storage/h2/migrations";
    private static final String INDEX_RESOURCE = MIGRATION_DIRECTORY + "/index.xml";
    private static final Pattern SOURCE_PATTERN = Pattern.compile("p(\\d+)__(.+)\\.sql");

    @Override
    public List<IMigration> getMigrations() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            try (InputStream input = new ClassPathResource(
                    INDEX_RESOURCE,
                    H2MigrationProvider.class.getClassLoader()
            ).getInputStream()) {
                Element root = factory.newDocumentBuilder().parse(input).getDocumentElement();
                if (!"migrations".equals(root.getTagName())) {
                    throw new IllegalStateException("Invalid H2 migration index root element");
                }
                return parseMigrations(root.getChildNodes());
            }
        } catch (IOException | ParserConfigurationException | SAXException exception) {
            throw new IllegalStateException("Could not read H2 migration index " + INDEX_RESOURCE, exception);
        }
    }

    private List<IMigration> parseMigrations(NodeList nodes) {
        List<IMigration> migrations = new ArrayList<>();
        Set<Integer> versions = new HashSet<>();
        int previousVersion = 0;
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!(node instanceof Element migration) || !"migration".equals(migration.getTagName())) {
                throw new IllegalStateException("Unexpected element in H2 migration index");
            }

            String source = migration.getAttribute("src");
            validateSource(source);
            Matcher matcher = SOURCE_PATTERN.matcher(source);
            if (!matcher.matches()) {
                throw new IllegalStateException("Invalid H2 migration filename: " + source);
            }

            int version = parseVersion(matcher.group(1), source);
            if (!versions.add(version)) {
                throw new IllegalStateException("Duplicate H2 migration version: " + version);
            }
            if (version <= previousVersion) {
                throw new IllegalStateException("H2 migrations must be listed in ascending version order");
            }
            previousVersion = version;
            String description = matcher.group(2).replace('-', ' ').replace('_', ' ');
            migrations.add(new H2SqlMigration(version, description, source));
        }

        if (migrations.isEmpty()) {
            throw new IllegalStateException("H2 migration index contains no migrations");
        }
        return List.copyOf(migrations);
    }

    static int versionFromSource(String source) {
        Matcher matcher = SOURCE_PATTERN.matcher(source);
        if (!matcher.matches()) {
            throw new IllegalStateException("Invalid H2 migration filename: " + source);
        }
        return parseVersion(matcher.group(1), source);
    }

    static void validateSource(String source) {
        if (source.isBlank()) {
            throw new IllegalStateException("H2 migration source cannot be blank");
        }

        Path path = Path.of(source);
        if (path.isAbsolute()
                || !path.equals(path.normalize())
                || path.normalize().startsWith("..")
                || !path.toString().replace('\\', '/').equals(source)
                || !source.endsWith(".sql")) {
            throw new IllegalStateException("Invalid relative H2 migration source: " + source);
        }
    }

    private static int parseVersion(String value, String source) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid H2 migration version in " + source, exception);
        }
    }
}

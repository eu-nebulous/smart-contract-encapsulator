-- Create the individual databases for each CA
CREATE DATABASE ca_orderer OWNER fabricca;
CREATE DATABASE ca_org1 OWNER fabricca;  
CREATE DATABASE ca_org2 OWNER fabricca;

-- Grant all privileges
GRANT ALL PRIVILEGES ON DATABASE ca_orderer TO fabricca;
GRANT ALL PRIVILEGES ON DATABASE ca_org1 TO fabricca;
GRANT ALL PRIVILEGES ON DATABASE ca_org2 TO fabricca;

-- Log successful creation
\echo 'Databases created successfully'


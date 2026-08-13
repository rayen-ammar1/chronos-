import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
  url: "http://localhost:8180",       // Your Keycloak server URL
  realm: "chronos",                   // Your Keycloak realm
  clientId: "chronos-frontend"             // The Client ID you created in Step 1
});

export default keycloak;
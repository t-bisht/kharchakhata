# User login and token capture flow

## Intent

- Create a google login screen where a user can login using their gmail_id
- Post successful some details of the user should be stored in a database
- These details would be used by other engines to do their work

## Database

- Database would be newly setup
    - Database name: kharchakhata
    - username : tb
    - password : tb
- Choice of database : postgrese
- Table to create
    - khata_users
        - cols
            - user_id : auto_increment as the record is entered in the database
            - email : user email
            - access_token : google access token
            - access_token_expiry : expiry time stamp
            - refresh_token : refresh token to request new access token
            - updates_at : time stamp when refresh token updated the access token

- Location
    - SQL scripts would stay under infra/postgres

## Login flow

### Sequence diagram for interaction between the engines

```plantuml

actor User
participant Web_UI as Web
participant login_engine as le
participant Google_OAuth as Google
database DB

User -> Web : Click "Login with Google"

Web -> le : GET /auth/google

le -> le : Build OAuth URL\n(client_id, redirect_uri,\nstate, scopes)

le --> Web : HTTP 302 Redirect

Web -> Google : Navigate to OAuth URL

User -> Google : Authenticate & Grant Consent

alt Login Successful

    Google --> Web : HTTP 302 Redirect\nLocation: /oauth/callback?code=...

    Web -> le : GET /oauth/callback?code=...

    le -> Google : Exchange authorization code

    Google --> le : access_token\nrefresh_token\nid_token

    le -> le : Verify id_token

    le -> DB : Save/Update User with tokens

    le -> le : Create Application Session (JWT based session for future use)

    le --> Web : HTTP 302 Redirect /dashboard\n(Set JWT/Cookie)

end
```

## UI

- Start UI with a two pager component
    - Page 1 : A login page to use google login
    - Page 2 : A welcome page showing you are authenticated

- UI Theme
    - Use a modern analytical theme
    - Make sure you are able to define a skill file for an agent to work on react based web folder
        - Specify styles
        - Specify the reusable components

- Location
    - The code would stay under the /web folder
    - This should be a docker based deployment
    - Locally it should be able to run using npm run dev

## Backend - login_engine

- Specification
    - Java spring boot based engine facilitating actions on user

- Run capability
    - Ability to run locally
        - should read app_credentials.json file using an env variable or using the application.yml properties
        - In this case both UI and backend would be running on local host to should be able to form a callback URL
          accordingly

    - Ability to run via docker
        - Should be able to read the app_credentials.json file using an env variable
        - should be able to construct the callback URL to WEB using correct server name in docker compose setup



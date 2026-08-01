# Identity

## IDN-001 Login

<a id="idn-001-login"></a>

```mermaid
flowchart LR
  Client --> Login
```

<a id="idn-001-login-dev"></a>

```mermaid
flowchart LR
  AuthController --> AuthApplicationService
```

### Target architecture flow

```mermaid
flowchart LR
  Client --> IdentityUseCase
```

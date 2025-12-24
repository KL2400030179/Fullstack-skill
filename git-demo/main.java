public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from initial commit");
        // CHANGE_ME
        param(
  [string]$ProjectDir = "git-demo-project",
  [string]$RepoName = "git-demo-project",
  [string]$GitUserName = "Your Name",
  [string]$GitUserEmail = "you@example.com",
  [string]$GitHubToken = "",    # optional: GitHub personal access token with repo scope
  [string]$Visibility = "public" # "public" or "private"
)

Set-StrictMode -Version Latest

# 1. Create folder and files
New-Item -ItemType Directory -Path $ProjectDir -Force | Out-Null
Set-Location $ProjectDir

@'
class Main {
    public static void main(String[] args) {
        System.out.println("Version 1");
    }
}
'@ | Out-File -Encoding UTF8 main.java

"Initial notes for the repo." | Out-File -Encoding UTF8 notes.txt

# 2. Init git and configure user
git init
git config user.name $GitUserName
git config user.email $GitUserEmail

# 3. Stage and commit initial files
git add main.java notes.txt
git commit -m "Initial commit: add main.java and notes.txt"

# 4. Create GitHub repo (prefer gh CLI; fallback to API with token)
$remoteAdded = $false
if (Get-Command gh -ErrorAction SilentlyContinue) {
    gh repo create $RepoName --$Visibility --source=. --remote=origin --push --confirm
    $remoteAdded = $true
} elseif ($GitHubToken) {
    $payload = @{ name = $RepoName; private = ($Visibility -eq "private") } | ConvertTo-Json
    $headers = @{ Authorization = "token $GitHubToken"; "User-Agent" = "PS" }
    $resp = Invoke-RestMethod -Method Post -Uri "https://api.github.com/user/repos" -Headers $headers -Body $payload
    if ($resp.ssh_url) {
        git remote add origin $resp.ssh_url
        git branch -M main
        git push -u origin main
        $remoteAdded = $true
    } else {
        Write-Host "GitHub API did not return a repo URL. Remote not added."
    }
} else {
    Write-Host "No 'gh' CLI and no GitHub token provided — skipping remote creation. Add remote manually if desired."
}

# 5. Create feature-update branch and commit a change
git checkout -b feature-update
(Get-Content main.java) -replace 'Version 1', 'Version 2 from feature' | Set-Content main.java
git add main.java
git commit -m "feature: update main.java message (feature-update)"

# 6. Create bug-fix branch (from main) and make a conflicting change
git checkout main
git checkout -b bug-fix
(Get-Content main.java) -replace 'Version 1', 'Version 2 from bugfix' | Set-Content main.java
git add main.java
git commit -m "fix: update main.java message (bug-fix)"

# 7. Merge feature-update into main
git checkout main
git merge feature-update --no-edit

# 8. Merge bug-fix into main and resolve conflict if any
$mergeOutput = git merge bug-fix 2>&1
if ($LASTEXITCODE -ne 0 -and ($mergeOutput -match "CONFLICT" -or $mergeOutput -match "Automatic merge failed")) {
    Write-Host "Merge conflict detected. Writing resolved version and committing."
    @'
class Main {
    public static void main(String[] args) {
        System.out.println("Resolved: feature + bugfix");
    }
}
'@ | Set-Content main.java
    git add main.java
    git commit -m "chore: resolve merge conflict between feature-update and bug-fix"
    Write-Host "Conflict resolved and committed."
} else {
    Write-Host "Merged bug-fix without conflicts."
}

# Push branches if remote exists
if ($remoteAdded -and (git remote)) {
    git push -u origin main
    git push -u origin feature-update
    git push -u origin bug-fix
    Write-Host "Pushed branches to origin."
} else {
    Write-Host "No remote configured; skipping pushes."
}

Write-Host "Done. Repository at: $(Get-Location)"
    }
}

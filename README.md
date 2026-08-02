# Exvia
<img width="236" height="236" alt="logo" src="https://github.com/user-attachments/assets/049d1e07-5866-4c51-bcb8-d0a83479a0b1" />

*Exvia*, originates from Latin, *ex* means Out, and *via* means track, where it signify the act of tracking and monitoring every out (expenses) of your finance.

Minimal Android expense editor backed directly by JSON files in a GitHub repository, made especially for Programmers.

The app uses GitHub's REST Contents API. A write replaces the selected JSON file using its latest SHA, and GitHub creates the commit directly on `main`. No Git executable or local `.git` directory is stored on the phone.

This is intentional, because this app are made to respect the user's privacy. Meaning, we will not own your expenses, we will not monitor your income, our capability is therefore to provide an application where user are given maximum responsibility and freedom, to manage and to monitor their track. But at the cost, where user must create their own account, to control their own database. Additionally, an abundant knowledge of SQLite and JS is even much better.

# Showcase

https://github.com/user-attachments/assets/1a5c1559-9435-4bd3-8ea8-ae7c5da98057

# Installation
1. Create your own GitHub repository, and ensure it is private, and your GitHub PAT is initialized
2. Within the GitHub repository, initialize a folder within, it can be anything, I personally created `Financial/`
3. Within the folder, create your first expenses `.json`. It track any .json file under `Financial/`, I personally name it this way `@MMMM_expenses.json`
4. For the template, copy and paste this into the expenses file:
```
[
  {
    "date": "Test",
    "price": "999",
    "code": "Food",
    "description": "coffee"
  }
]
```
5. Ensure every changes it commited, now git clone this repository on your Android Studio `git clone https://github.com/3oFiz4/Exvia`
6. Install the apk
7. When you open the app for the first time, you will be instructed to input everything to the config (autosaves) it will require you to input your GitHub PAT, copy your GitHub PAT and paste it there. This is the "password" or "token" to access your repository, because it is private.
8. Enjoy

# Features
1. CRUD (Create-Read-Update-Delete) Operation for Expenses, Income, and for every Table.
2. Built-in Filtering via SQLite syntax (e.g., SELECT * WHERE ...)
3. No managed databases, the app is not controlled by the author. You create your own, you manage it.
4. Automatic and smart synchronization between application with database
5. Configurable color for table's value
6. Extensive configuration, this includes GitHub as Database, Color, Scheme & Display, Custom metric, and Custom plot
7. Built-in Tutorial for first-time installation
8. Extensive statistical and financial metric, you can add your own if you want to.
9. Multi-file access and synchronization
10. Distinguishable logo and name
11. Beautiful font family

# Incoming Features
1. Minimal, Lightweight, and yet Accurate voice recognition model that can be triggered by saying the word "Fin", and will listen for any word uttered by the user, and amending what is spoken to the database at a time
2. More plots
3. A image scanner feature to scan image whether at real-time or assuming there is a given folder, parse the image and apply the operation to the database directly. This is useful, especially when most of your transactions relied on digital banking app, so you do not have to type anymore, but instead the app will do it's job.
4. Add an option whether to Disable/Enable Developer option, therefore offering this app even to lay-people that has no strong knowledge whether in programming, finance, or statistics.
5. Add an automatic operation to create a GitHub account, assuming the user is lay-people
6. Add an extended finance for Investment, Debt, and more. (By theory, you could imitate this in the app right now, without having this feature amended for next release. By categorizing your specific data, say INV (for Investment), create the field necessary, such as Future Value (FV), you could create a custom metric that calculates them)

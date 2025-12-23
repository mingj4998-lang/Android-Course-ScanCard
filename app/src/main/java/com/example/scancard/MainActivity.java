package com.example.scancard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.scancard.R;

public class MainActivity extends AppCompatActivity {

    private Button btnToIdCard;
    private Button btnToBankCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToIdCard = findViewById(R.id.btn_to_idcard);
        btnToBankCard = findViewById(R.id.btn_to_bankcard);

        btnToIdCard.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, IdCardActivity.class));
        });

        btnToBankCard.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, BankCardActivity.class));
        });
    }
}
